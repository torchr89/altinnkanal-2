package no.nav.altinnkanal.soap;

import io.prometheus.client.Counter;
import io.prometheus.client.Summary;
import no.altinn.webservices.OnlineBatchReceiverSoap;
import no.nav.altinnkanal.avro.ExternalAttachment;
import no.nav.altinnkanal.entities.TopicMapping;
import no.nav.altinnkanal.services.KafkaService;
import no.nav.altinnkanal.services.TopicService;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.util.Base64;

@Service
public class OnlineBatchReceiverSoapImpl implements OnlineBatchReceiverSoap {
    private final Base64.Encoder base64Encoder = Base64.getEncoder();
    private final Logger logger = LogManager.getLogger("AltinnKanal");

    private final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    private final TopicService topicService;
    private final KafkaService kafkaService;

    private static final Counter requestsTotal = Counter.build()
            .name("requests_total").help("Total requests.")
            .labelNames("sc", "sec").register();
    private static final Counter requestsSuccess = Counter.build()
            .name("requests_success").help("Total successful requests.")
            .labelNames("sc", "sec").register();
    private static final Counter requestsFailedDisabled = Counter.build()
            .name("requests_disabled").help("Total failed requests due to disabled SC and SEC.")
            .labelNames("sc", "sec").register();
    private static final Counter requestsFailedMissing = Counter.build()
            .name("requests_missing").help("Total failed requests due to missing fields.")
            .labelNames("sc", "sec").register();
    private static final Counter requestsFailedError = Counter.build()
            .name("requests_error").help("Total failed requests due to error.")
            .labelNames("sc", "sec").register();

    private static final Summary requestSize = Summary.build()
            .name("request_size_bytes_sum").help("Request size in bytes.")
            .register();
    private static final Summary requestTime = Summary.build()
            .name("request_time_ms").help("Request time in milliseconds.")
            .register();

    @Autowired
    public OnlineBatchReceiverSoapImpl(TopicService topicService, KafkaService kafkaService) throws Exception { // TODO: better handling of exceptions
        this.topicService = topicService;
        this.kafkaService = kafkaService;
    }

    public String receiveOnlineBatchExternalAttachment(String username, String passwd, String receiversReference, long sequenceNumber, String dataBatch, byte[] attachments) {
        String serviceCode = null;
        String serviceEditionCode = null;
        Summary.Timer requestLatency = requestTime.startTimer();

        try {
            ExternalAttachment externalAttachment = toAvroObject(dataBatch);

            serviceCode = externalAttachment.getSc().toString();
            serviceEditionCode = externalAttachment.getSec().toString();
            requestsTotal.labels(serviceCode, serviceEditionCode).inc();

            TopicMapping topicMapping = topicService.getTopicMapping(serviceCode, serviceEditionCode);

            if (topicMapping == null || !topicMapping.isEnabled()) {
                if (topicMapping == null) requestsFailedMissing.labels(serviceCode, serviceEditionCode).inc();
                else requestsFailedDisabled.labels(serviceCode, serviceEditionCode).inc();
                return "FAILED_DO_NOT_RETRY";
            }

            // TODO: Validate/check if received metadata matches sent record?
            RecordMetadata metadata = kafkaService.publish(topicMapping.getTopic(), externalAttachment).get();

            requestLatency.observeDuration();
            requestSize.observe(metadata.serializedValueSize());
            requestsSuccess.labels(serviceCode, serviceEditionCode).inc();

            return "OK";
        } catch (Exception e) {
            logger.error("Failed to send a ROBEA request to Kafka", e);
            requestsFailedError.labels(serviceCode, serviceEditionCode).inc();
            return "FAILED";
        }
    }

    public ExternalAttachment toAvroObject(String dataBatch) throws Exception {
        StringReader reader = new StringReader(dataBatch);
        XMLStreamReader xmlReader = xmlInputFactory.createXMLStreamReader(reader);
        String serviceCode = null;
        String serviceEditionCode = null;
        String archiveReference = null;

        while (xmlReader.hasNext()) {
            int eventType = xmlReader.next();
            if (eventType == XMLEvent.ATTRIBUTE) {
                System.out.println("ATTRIBUTE");
                System.out.println(xmlReader.getElementText());
            } else if (eventType == XMLEvent.START_ELEMENT) {
                String tagName = xmlReader.getLocalName();
                if (tagName.equals("ServiceCode")) {
                    xmlReader.next();
                    serviceCode = xmlReader.getText();
                } else if (tagName.equals("ServiceEditionCode")) {
                    xmlReader.next();
                    serviceEditionCode = xmlReader.getText();
                } else if (tagName.equals("DataUnit")) {
                    archiveReference = xmlReader.getAttributeValue(null, "archiveReference");
                }
            }
            if (archiveReference != null && serviceCode != null && serviceEditionCode != null)
                break;
        }

        xmlReader.close();

        String batchBase64 = base64Encoder.encodeToString(dataBatch.getBytes());


        ExternalAttachment externalAttachment = ExternalAttachment.newBuilder()
                .setSc(serviceCode)
                .setSec(serviceEditionCode)
                .setArchRef(archiveReference)
                .setBatch(batchBase64)
                .build();

        logger.debug("Got a ROBEA request", externalAttachment);

        return externalAttachment;
    }
}

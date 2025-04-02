package sample;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.StatusLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;



@Controller
public class IndexController {
    private static final String DOCUMENTS_DIRECTORY = Paths.get(System.getProperty("user.dir"), "Documents").toString();
    private static final String DOCUMENT_FILENAME = "example.pdf";

    private static final Logger log = LoggerFactory.getLogger(IndexController.class);

    @Value("${prizmdoc.cloud.apiKey:#{null}}")
    private String cloudApiKey;

    @Value("${prizmdoc.pas.baseUrl:#{null}}")
    private String pasBaseUrl;

    @Value("${prizmdoc.pas.secretKey:#{null}}")
    private String pasSecretKey;

    @Value("${prizmdoc.hybridViewing.enabled:#{null}}")
    private boolean enableHybridViewing;

    @GetMapping("/")
    public String renderPage(Model model) throws IOException, HttpException {
        HttpClient httpClient = HttpClientBuilder.create().build();

        JsonArray allowedClientFileFormats = Json.createArrayBuilder().build();
        if(enableHybridViewing){
            allowedClientFileFormats = Json.createArrayBuilder().add("pdf").build();
        }

        // 1. Create a new viewing session
        HttpPost postRequest = new HttpPost(pasBaseUrl + "ViewingSession");
        JsonObject body = Json.createObjectBuilder()
            .add("source", Json.createObjectBuilder()
                .add("type", "upload")
                .add("displayName", DOCUMENT_FILENAME))
            .add("allowedClientFileFormats", allowedClientFileFormats)
            .build();

        if (cloudApiKey != null) {
            postRequest.addHeader("Acs-Api-Key", cloudApiKey);
        }

        postRequest.addHeader("Content-Type", "application/json");
        log.info(body.toString());
        postRequest.setEntity(new StringEntity(body.toString()));

        log.info("Creating viewing session");

        HttpResponse response = httpClient.execute(postRequest);
        if (response.getCode() != 200) {
            throw new HttpException("POST /ViewingSession HTTP request returned an error: " + (new StatusLine(response).toString()) + " " + EntityUtils.toString(((ClassicHttpResponse)response).getEntity()));
        }

        String responseJson = EntityUtils.toString(((ClassicHttpResponse)response).getEntity());
        log.debug("Received JSON: {}", responseJson);

        // 2. Send the viewingSessionId and viewer assets to the browser right away so
        // the viewer UI can start loading.
        String viewingSessionId;
        try (JsonReader reader = Json.createReader(new StringReader(responseJson))) {
            viewingSessionId = reader.readObject().getString("viewingSessionId");
            log.info("Received viewingSessionId {}", viewingSessionId);
            model.addAttribute("viewingSessionId", viewingSessionId);
        }

        // 3. Upload the source document to PrizmDoc so that it can start being
        // converted to SVG.
        // The viewer will request this content and receive it automatically once it is
        // ready.
        // We do this part on a background thread so that we don't block the HTML from
        // being
        // sent to the browser.
        new Thread(() -> {
            File document = new File(Paths.get(DOCUMENTS_DIRECTORY, DOCUMENT_FILENAME).toString());
            HttpPut putRequest = new HttpPut(pasBaseUrl + "ViewingSession/u" + viewingSessionId + "/SourceFile");

            if (cloudApiKey != null) {
                putRequest.addHeader("Acs-Api-Key", cloudApiKey);
            }

            if (pasSecretKey != null) {
                putRequest.addHeader("Accusoft-Secret", pasSecretKey);
            }

            putRequest.addHeader("Content-Type", "application/octet-stream");
            putRequest.setEntity(new FileEntity(document, ContentType.APPLICATION_OCTET_STREAM));

            try {
                log.info("Uploading source document");
                HttpResponse putResponse = httpClient.execute(putRequest);
                if (putResponse.getCode() != 200) {
                    throw new HttpException("PUT /SourceFile HTTP request returned an error: " + (new StatusLine(response).toString()) + " " + EntityUtils.toString(((ClassicHttpResponse)response).getEntity()));
                }
            } catch (Exception e) {
                log.error(e.toString());
            }
        }).start();

        // Render the "index" view, sending the HTML to the browser
        return "index";
    }
}

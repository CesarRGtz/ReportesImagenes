package com.teosa.app.prototipo.network;

import com.google.gson.reflect.TypeToken;
import com.teosa.app.prototipo.data.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class HttpReportClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private volatile String baseUrl;

    public HttpReportClient(String baseUrl) { setBaseUrl(baseUrl); }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", ""); }
    public String getBaseUrl() { return baseUrl; }

    public boolean health() {
        try { return request("GET", "/api/health", null).statusCode() == 200; }
        catch (Exception ex) { return false; }
    }

    public SaveResponse saveReport(ReportTransfer transfer) throws IOException {
        return fromResponse(request("POST", "/api/reports", JsonSupport.GSON.toJson(transfer)), SaveResponse.class);
    }

    public List<ReportSummary> listReports(String query) throws IOException {
        String path = "/api/reports?q=" + encode(query == null ? "" : query);
        HttpResponse<String> response = request("GET", path, null);
        ensureOk(response);
        return JsonSupport.GSON.fromJson(response.body(), new TypeToken<List<ReportSummary>>(){}.getType());
    }

    public List<VersionSummary> listVersions(String id) throws IOException {
        HttpResponse<String> response = request("GET", "/api/reports/" + encode(id) + "/versions", null);
        ensureOk(response);
        return JsonSupport.GSON.fromJson(response.body(), new TypeToken<List<VersionSummary>>(){}.getType());
    }

    public ReportTransfer loadReport(String id, int version) throws IOException {
        return fromResponse(request("GET", "/api/reports/" + encode(id)
                + "/versions/" + version, null), ReportTransfer.class);
    }

    public void deleteReport(String id) throws IOException {
        ensureOk(request("DELETE", "/api/reports/" + encode(id), null));
    }

    public void deleteVersion(String id, int version) throws IOException {
        ensureOk(request("DELETE", "/api/reports/" + encode(id) + "/versions/" + version, null));
    }

    public List<TemplateDefinition> listTemplates() throws IOException {
        HttpResponse<String> response = request("GET", "/api/templates", null);
        ensureOk(response);
        return JsonSupport.GSON.fromJson(response.body(), new TypeToken<List<TemplateDefinition>>(){}.getType());
    }

    public void saveTemplate(TemplateDefinition template) throws IOException {
        ensureOk(request("POST", "/api/templates", JsonSupport.GSON.toJson(template)));
    }

    public void deleteTemplate(String name) throws IOException {
        ensureOk(request("DELETE", "/api/templates/" + encode(name), null));
    }

    private HttpResponse<String> request(String method, String path, String body) throws IOException {
        if (baseUrl.isBlank()) throw new IOException("Servidor no configurado");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Operación interrumpida", ex);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Dirección del servidor inválida", ex);
        }
    }

    private <T> T fromResponse(HttpResponse<String> response, Class<T> type) throws IOException {
        ensureOk(response);
        return JsonSupport.GSON.fromJson(response.body(), type);
    }

    private void ensureOk(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Servidor: " + response.body());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

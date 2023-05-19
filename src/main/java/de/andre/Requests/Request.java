package de.andre.Requests;


import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Request {
    public static HttpClient client = HttpClient.newHttpClient();
    private static boolean isRandomUserAgentPresent = false;
    private static Class c = null;
    private static Object obj = null;
    private static Method method = null;

    static {
        try {
            c = Class.forName("com.github.mkstayalive.randomuseragent.RandomUserAgent");
            obj = c.getDeclaredConstructor().newInstance();
            method = obj.getClass().getMethod("getRandomUserAgent");
            isRandomUserAgentPresent = true;
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            Logger.getGlobal().warning("RandomUserAgent could not be loaded.");
        }
    }

    private String url;
    private RequestMode mode = RequestMode.GET;
    private JSONObject headers = new JSONObject();
    private HashMap<String, Object> body = new HashMap<>();
    private boolean useRandomUserAgent = false;

    public Request(String url, RequestMode mode) {
        this.url = url;
        this.mode = mode;
    }

    public Request(String url) {
        this.url = url;
    }

    /**
     * Parses the ip address form <a href="http://checkip.amazonaws.com/">http://checkip.amazonaws.com/</a>
     *
     * @param checkIP set this to true to verify that an ip address is an ipv4 address, if the check failed the plain string of the html will be returned
     * @return ipv4 as string
     */
    public static String getGlobalIP(boolean checkIP) {
        String url = "http://checkip.amazonaws.com/";
        String html = new Request(url).setUseRandomUserAgent(true).makeRequest().getString("html");
        if (checkIP) {
            ArrayList<String> matches = RegexStringDataBase.PARSE_IP.parseFromString(html);
            if (!matches.isEmpty()) {
                return matches.get(0);
            }
            Logger.getGlobal().warning("IP-Check failed");
        }
        return html.stripTrailing();
    }

    /**
     * See {@link Request#getGlobalIP(boolean)} for the implementation
     */
    public static String getGlobalIP() {
        return getGlobalIP(true);
    }


    /**
     * Returns the local IP of this device, if an error occurs it will return null
     *
     * @return local ipv4 of this device
     */

    @Nullable
    public static String getLocalIP() {
        ArrayList<String> possibleIPAddresses = new ArrayList<>();
        ArrayList<String> allIPs = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            Logger.getGlobal().log(Level.SEVERE, "Failed to get NetworkInterface.getNetworkInterfaces due to an SocketException.");
            e.printStackTrace();
            return null;
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            // filters out 127.0.0.1 and inactive interfaces
            try {
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    allIPs.add(addresses.nextElement().getHostAddress());
                }
            } catch (SocketException e) {
            }
        }
        for (String s : allIPs) {
            ArrayList<String> results = RegexStringDataBase.PARSE_IP.parseFromString(s);
            if (results.size() > 0) {
                possibleIPAddresses.add(s);
            }
        }
        if (possibleIPAddresses.size() >= 2) {
            try {
                InetAddress ip = InetAddress.getLocalHost();
                possibleIPAddresses.remove(ip.toString().split("/")[1]);
                return possibleIPAddresses.get(0);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        } else if (possibleIPAddresses.size() == 1) {
            return possibleIPAddresses.get(0);
        }

        return null;
    }

    /**
     * gets a {@link BufferedImage} from the given url or null if any {@link IOException} occurred
     *
     * @param url the url of an image
     * @return a BufferedImage or null
     */
    public static @Nullable BufferedImage getImage(String url) {

        try (InputStream in = new URL(url).openStream()) {
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * saves the image from the url at the location of the {@link Path} and returns true if it was successful
     *
     * @param url the url of an image
     * @param p   {@link Path} where the image should be saved to (the path should include following elements: path to directory + image name + file extension)
     * @return boolean depending on whether the image was saved successfully
     */
    public static boolean saveImage(String url, Path p) {
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, p);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Converts a {@link HashMap} to a {@link java.net.http.HttpRequest.BodyPublisher}.<br>
     * To send a file you have to set the {@link Path} to the given file as the value of the HashMap entry.
     *
     * @param bodyMap  the HashMap with the data; key(String) to value(Object) connection
     * @param boundary a special string that indicates a entry is starting/ending, in order for the server to know where and when it has to split the values to get the correct result
     * @return parameter bodyMap as {@link HttpRequest.BodyPublisher}
     */
    public static HttpRequest.BodyPublisher toBodyPublisher(HashMap<String, Object> bodyMap, String boundary) {
        if (bodyMap.keySet().isEmpty()) return HttpRequest.BodyPublishers.noBody();

        List<String> lines = new ArrayList<>();

        String boundaryString = "--" + boundary + "\r\nContent-Disposition: form-data; name=";

        bodyMap.forEach((key, value) -> {
            try {
                lines.add(
                        boundaryString +
                                (value instanceof Path path ?
                                        String.format("\"%s\";filename\"%s\"\r\nContent-Type: %s\r\n\r\r%s\r\n", key, path.getFileName(), Files.probeContentType(path), Files.readString(path)) :
                                        String.format("\"%s\"\r\n\r\n%s\r\n", key, value)
                                )
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return HttpRequest.BodyPublishers.ofByteArrays(new ArrayList<>() {{
            lines.stream().map(x -> x.getBytes(StandardCharsets.UTF_8)).forEach(this::add);
            add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        }});
    }

    /**
     * @return if the dependency for RandomUserAgent is loaded. If the dependency is not loaded, no random UserAgent will be able to be put into the headers. This will also be print into the logs.
     */
    public static boolean isIsRandomUserAgentPresent() {
        return isRandomUserAgentPresent;
    }

    public JSONObject makeRequest() {
        return httpRequestToJsonObject(
                prepareRequest(url, headers, toBodyPublisher(body, String.valueOf(System.nanoTime() * new Random().nextLong())), mode.mode(), useRandomUserAgent)
        );
    }

    /**
     * Takes all the needed parameters and returns a {@link HttpRequest}
     *
     * @param url     the URL as a string
     * @param headers the headers that should be put into the {@link HttpRequest}; the random UserAgent will be set automatically if the value is true
     * @param body    body as BodyPublisher; see {@link Request#toBodyPublisher(HashMap, String)} for additional information
     * @param mode    mode as String; see {@link RequestMode}
     * @return {@link HttpRequest} with all parameters set as given
     */
    public HttpRequest prepareRequest(String url, JSONObject headers, HttpRequest.BodyPublisher body, String mode, boolean useRandomUserAgent) {
        HttpRequest.Builder requestBuilder = null;
        try {
            requestBuilder = HttpRequest.newBuilder()
                    .uri(new URL(url.replace(" ", "%20")).toURI()).
                    method(mode, body);
        } catch (URISyntaxException | MalformedURLException e) {
            e.printStackTrace();
        }

        try {
            if (isRandomUserAgentPresent && !headers.keySet().contains("UserAgent") && useRandomUserAgent) {
                headers.put("UserAgent", method.invoke(obj));
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }


        if (requestBuilder == null) return null;
        HttpRequest.Builder finalRequestBuilder = requestBuilder;
        headers.keys().forEachRemaining(x -> finalRequestBuilder.header(x, headers.getString(x)));
        return requestBuilder.build();
    }

    /**
     * Sends a {@link HttpRequest}, and parses a {@link JSONObject} for more information about the return value see {@link Request#responseToJSON(HttpResponse)}
     * @return a {@link JSONObject}
     */
    public JSONObject httpRequestToJsonObject(HttpRequest request) {
        return responseToJSON(httpRequestToResponse(request));
    }

    /**
     * Sends an async {@link HttpRequest} and returns the corresponding {@link HttpResponse}
     * @return a {@link HttpResponse}
     * */
    public HttpResponse<String> httpRequestToResponse(HttpRequest request) {
        HttpResponse<String> response = null;
        try {
            CompletableFuture<HttpResponse<String>> futureResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            response = futureResponse.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return response;
    }

    /**
     * @return if the response contains...<br>
     * <pre>
     * ... a {@link JSONObject} -> {@link JSONObject} <br>
     * ... a {@link JSONArray} -> {"array": {@link JSONArray}}<br>
     * ... something other -> {"html": html as a {@link String}}<br>
     * </pre>
     *
     * if an error occurs the returning {@link JSONObject} will return the html and error-code. Structure:<br>
     * <pre>
     * {"error-code": {@link HttpResponse#statusCode()}, "html":{@link HttpResponse#body()}}
     * </pre>
     *
     *
     * */
    public JSONObject responseToJSON(@Nullable HttpResponse<String> response) {
        if (response != null && response.statusCode() >= 400) {
            return new JSONObject() {{
                put("error-code", response.statusCode());
                put("html", response.body());
            }};
        }
        if (response == null) {
            return new JSONObject("");
        }
        try {
            return new JSONObject(response.body());
        } catch (JSONException ignored) {
        }
        try {
            return new JSONObject().put("array", new JSONArray(response.body()));
        } catch (JSONException ignored) {
        }

        return new JSONObject() {{
            put("html", response.body());
        }};
    }

    /**
     * Adds a string to an existing value. If no value exists the string is the new value
     * @param key the key to the value, the string should be added to
     * @param value the string that should be added
     * @return {@link Request}
     * */
    public Request addHeader(String key, String value) {
        this.headers.put(key, this.headers.keySet().contains(key) ? this.headers.getString(key) + value : value);
        return this;
    }

    public Request setHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public Request setBodyEntry(String key, Object value) {
        this.body.put(key, value);
        return this;
    }

    public JSONObject getHeaders() {
        return headers;
    }

    public HashMap<String, Object> getBody() {
        return body;
    }

    public Request setBody(HashMap<String, Object> body) {
        this.body = body;
        return this;
    }

    public boolean isUseRandomUserAgent() {
        return useRandomUserAgent;
    }

    public Request setUseRandomUserAgent(boolean useRandomUserAgent) {
        this.useRandomUserAgent = useRandomUserAgent;
        return this;
    }

    public Request setUrl(String url) {
        this.url = url;
        return this;
    }

    public Request setMode(RequestMode mode) {
        this.mode = mode;
        return this;
    }
}

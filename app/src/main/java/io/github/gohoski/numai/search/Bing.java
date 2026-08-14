package io.github.gohoski.numai.search;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.gohoski.numai.api.ApiClient;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiRequest;
import io.github.gohoski.numai.api.ApiResponse;

/**
 * Created by Gleb on 30.07.2026.
 */
class Bing implements SearchEngine {
    // new cookies on each Bing instance is intentional due to CAPTCHA
    private final Map<String, String> cookieStore = new HashMap<String, String>();
    private static int[] prevDims = null;

    private ApiClient api;
    private String userAgent;
    private Map<String, String> fp;

    Bing() {
        api = new ApiClient(null);
        userAgent = "Mozilla/5.0 (Linux; U; Android " + this.getAndroid() + "; en-us; generic) AppleWebKit/525.10+ (KHTML, like Gecko) Version/3.0.4 Mobile Safari/523.12.2";
    }

    private String getAndroid() {
        switch(android.os.Build.VERSION.SDK) {
            case "1": return "1.0";
            case "2": return "1.1";
            case "3": return "1.5";
            case "4": return "1.6";
            case "5": return "2.0";
            case "6": return "2.0.1";
            case "7": return "2.1";
            case "8": return "2.2";
            default: return "2.3";
        }
    }

    @Override
    public List<SearchResult> search(String q) throws SearchException, ApiError, IOException {
        // Bing is still accessible over HTTP for old Android User-Agent
        ApiRequest request = new ApiRequest("http://www.bing.com", "/search", "GET")
                .addParam("q", q)
                .addParam("qs", "ds")
                .addParam("form", "QBLH");
        addStandardHeaders(request);

        ApiResponse response = executeWithRetry(request);
        updateCookiesFromResponse(response);
        String html = readResponseAsString(response);
        String queryUrl = request.getBaseUrl() + request.getEndpoint();

        // Check for PoW challenge
        // Usually, at first Bing doesn't require a PoW challenge. However, as soon as a threshold of a certain number of requests within
        // a certain period of time is reached, Bing enforces a Proof-of-work Hashcash challenge that is usually solved via modern JavaScript.
        // When Bing enforces a PoW challenge for a suspicious IP address, it intentionally returns fake search results that have nothing to do with the
        // search query. This may make bypassing it frustrating, but we can check if the challenge is enforced on the page by looking at this keyword.
        // This is just doing what the current at the time of writing JavaScript does—if the JavaScript changes often, we need to either often change this
        // Java solver or somehow bundle QuickJS, but that is unlikely.
        if (html.contains("var PoWConfig")) {
            Log.i("Bing", "PoW Challenge detected!!");
            String ct = matchRegex("\"ct\":\"([^\"]+)\"", html);
            int cd = Integer.parseInt(matchRegex("\"cd\":(\\d+)", html));

            // Initialize Page Telemetry
            initPageTelemetry(html);

            // CPT "L" Beacon
            sendCpt("L", queryUrl);
            try { Thread.sleep(50 + new Random().nextInt(100)); } catch (InterruptedException ignored) {}

            // HV Beacon
            String hvData = "[{\"T\":\"CI.BM\",\"FID\":\"CI\",\"Name\":\"HV\"}]";
            sendLs("Event.ClientInst", hvData, 4, null, queryUrl);

            // PoW StartTime
            long startTime = System.currentTimeMillis();
            sendPowLog("StartTime", String.valueOf(startTime), queryUrl);

            // Solve PoW
            long nonce = 0;
            long latency = 0;
            try {
                long[] powResult = solvePow(ct, cd);
                nonce = powResult[0];
                latency = powResult[1];
                Log.i("Bing", "Solved PoW: nonce=" + nonce + ", latency=" + latency + "ms");
            } catch (Exception e) {
                Log.e("Bing", "Failed to solve PoW: " + e.getMessage());
            }

            // PoW EndTime & Duration
            sendPowLog("EndTime", String.valueOf(System.currentTimeMillis()), queryUrl);
            sendPowLog("Duration", String.valueOf(latency), queryUrl);

            // Verify POST
            String postBody = buildFormBody(
                    "token", String.valueOf(nonce),
                    "issuer", "PoW",
                    "challenge", ct,
                    "difficulty", String.valueOf(cd),
                    "latency", String.valueOf(latency)
            );

            ApiRequest verifyReq = new ApiRequest("http://www.bing.com", "/bd/issuertoken/verify", "POST")
                    .addParam("IID", "PoW")
                    .addParam("SFX", "1")
                    .addParam("IG", fp.get("ig"));
            verifyReq.setBody(postBody);
            addStandardHeaders(verifyReq);
            verifyReq.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            verifyReq.addHeader("Origin", "http://www.bing.com");
            verifyReq.addHeader("Referer", queryUrl);

            ApiResponse verifyResp = executeWithRetry(verifyReq);
            updateCookiesFromResponse(verifyResp);
            closeResponseBody(verifyResp);

            // PoW VerCallStatus
            sendPowLog("VerCallStatus", "200", queryUrl);

            // BdVerify & SBI
            ApiRequest bdReq = new ApiRequest("http://www.bing.com", "/bd/verify", "GET")
                    .addParam("IID", "BdVerify")
                    .addParam("SFX", "1")
                    .addParam("IG", fp.get("ig"));
            addStandardHeaders(bdReq);
            bdReq.addHeader("Cache-Control", "no-cache");
            bdReq.addHeader("Pragma", "no-cache");
            bdReq.addHeader("Origin", "https://www.bing.com");
            bdReq.addHeader("Referer", "https://www.bing.com/");
            bdReq.addHeader("Sec-Fetch-Site", "same-site");
            ApiResponse bdResp = executeWithRetry(bdReq);
            updateCookiesFromResponse(bdResp);
            closeResponseBody(bdResp);

            ApiRequest sbiReq = new ApiRequest("http://www.bing.com", "/images/sbi", "GET")
                    .addParam("mmasync", "1")
                    .addParam("ig", fp.get("ig"))
                    .addParam("iid", ".5055")
                    .addParam("ptn", "Web")
                    .addParam("ep", "0")
                    .addParam("iconpl", "1")
                    .addParam("ajaxreq", "1");
            addStandardHeaders(sbiReq);
            sbiReq.addHeader("Referer", queryUrl);
            ApiResponse sbiResp = executeWithRetry(sbiReq);
            updateCookiesFromResponse(sbiResp);
            closeResponseBody(sbiResp);

            // PPT Beacon
            Random rand = new Random();
            int s = rand.nextInt(71) + 380;
            int e = rand.nextInt(101) + 600;
            String pptData = "{\"S\":" + s + ",\"E\":" + e + ",\"T\":0,\"I\":0,\"N\":{},\"M\":{}}";

            Map<String, String> pptParams = new HashMap<String, String>();
            pptParams.put("P", "SERP");
            pptParams.put("DA", fp.get("da"));
            sendLs("Event.PPT", pptData, 5, pptParams, queryUrl);

//            try { Thread.sleep(200 + rand.nextInt(200)); } catch (InterruptedException ignored) {}

            // Unload Telemetry
            sendCpt("A", queryUrl);
            sendPerfV2(queryUrl);
//            try { Thread.sleep(150); } catch (InterruptedException ignored) {}

            Log.i("Bing", "Fetching verified SERP");
            ApiRequest finalReq = new ApiRequest("http://www.bing.com", "/search", "GET")
                    .addParam("q", q)
                    .addParam("qs", "ds")
                    .addParam("form", "QBLH")
                    .addParam("rdr", "1")
                    .addParam("rdrig", fp.get("ig"));
            addStandardHeaders(finalReq);
            finalReq.addHeader("Referer", queryUrl);

            ApiResponse finalResp = executeWithRetry(finalReq);
            updateCookiesFromResponse(finalResp);
            try {
                html = readResponseAsString(finalResp);
            } catch (Exception _) { html = ""; _.printStackTrace(); }
        }

        List<SearchResult> results = parse(html);

        // Retry logic; if CAPTCHA or empty results , retry
        // For some reason sometimes the rdr request enforces a CAPTCHA.
        // However simply re-requesting without the rdr parameter bypasses this.
        // Probably not for long—need to check how reliable this is.
        if (html.contains("captcha_text") || results.isEmpty()) {
            Log.w("Bing", "CAPTCHA or no results detected. Retrying without rdr");
//            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}

//            String retryIg = (fp != null && fp.containsKey("ig")) ? fp.get("ig") : "";
            ApiRequest retryReq = new ApiRequest("http://www.bing.com", "/search", "GET")
                    .addParam("q", q)
                    .addParam("qs", "ds")
                    .addParam("form", "QBLH");
//            if (retryIg.length() > 0) {
//                retryReq.addParam("rdr", "1").addParam("rdrig", retryIg);
//            }
            addStandardHeaders(retryReq);
            retryReq.addHeader("Referer", queryUrl);

            ApiResponse retryResp = executeWithRetry(retryReq);
            updateCookiesFromResponse(retryResp);
            html = readResponseAsString(retryResp);
            results = parse(html);
        }

        Log.i("Bing", "HTML downloaded");
        return results;
    }

    private ApiResponse executeWithRetry(ApiRequest request) throws ApiError {
        final int maxAttempts = 3;
        ApiError lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return api.execute(request);
            } catch (ApiError e) {
                lastError = e;
                if (!e.isTimeout()) {
                    throw e;
                }
                Log.w("Bing", "Timeout on attempt " + attempt + "/" + maxAttempts + ": " + e.getMessage());
            }
        }
        throw lastError;
    }

    private void addStandardHeaders(ApiRequest request) {
        request.addHeader("User-Agent", userAgent);
        request.addHeader("Accept", "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        request.addHeader("Accept-Language", "en-US,en;q=0.9,ru;q=0.8");
        request.addHeader("Accept-Charset", "utf-8, iso-8859-1, utf-16, *;q=0.7");

        String cookieHeader = getCookieHeader();
        if (cookieHeader != null && cookieHeader.length() > 0) {
            request.addHeader("Cookie", cookieHeader);
        }
    }

    private String readResponseAsString(ApiResponse response) throws IOException {
        if (response == null || response.getBody() == null) return "";
        InputStream is = response.getBody();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }
    }

    private static void closeResponseBody(ApiResponse response) {
        if (response != null && response.getBody() != null) {
            try {
                response.getBody().close();
            } catch (IOException ignored) {}
        }
    }

    private static String buildFormBody(String... keyValuePairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i > 0) sb.append("&");
            try {
                sb.append(URLEncoder.encode(keyValuePairs[i], "UTF-8"))
                        .append("=")
                        .append(URLEncoder.encode(keyValuePairs[i + 1], "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {}
        }
        return sb.toString();
    }

    private static long[] solvePow(String ct, int cd) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] ctBytes = ct.getBytes("UTF-8");
        long nonce = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            md.reset();
            md.update(ctBytes);
            md.update(String.valueOf(nonce).getBytes("UTF-8"));
            byte[] hash = md.digest();

            if (checkLeadingZeros(hash, cd)) {
                long latency = System.currentTimeMillis() - t0;
                return new long[]{nonce, latency};
            }
            nonce++;
        }
    }

    private static boolean checkLeadingZeros(byte[] hash, int cd) {
        int fullBytes = cd / 2;
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0) return false;
        }
        if (cd % 2 != 0) {
            if ((hash[fullBytes] & 0xF0) != 0) return false;
        }
        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xff));
        }
        return sb.toString();
    }

    private void updateSrchCookie(Map<String, String> kv) {
        synchronized (cookieStore) {
            String oldVal = cookieStore.get("SRCHHPGUSR");
            if (oldVal == null) oldVal = "";

            Set<String> clientKeys = new HashSet<String>(Arrays.asList(
                    "CW", "CH", "SCW", "SCH", "DPR", "UTC", "PV", "BRW", "BRH",
                    "PREFCOL", "PRVCW", "PRVCH", "B", "EXLTT", "HV", "HVE"
            ));

            List<String> existing = new ArrayList<String>();
            if (oldVal.length() > 0) {
                String[] parts = oldVal.split("&");
                for (int i = 0; i < parts.length; i++) {
                    String p = parts[i];
                    if (p.length() > 0) {
                        String key = p.split("=")[0];
                        if (!clientKeys.contains(key)) {
                            existing.add(p);
                        }
                    }
                }
            }

            for (Map.Entry<String, String> entry : kv.entrySet()) {
                existing.add(entry.getKey() + "=" + entry.getValue());
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < existing.size(); i++) {
                if (i > 0) sb.append("&");
                sb.append(existing.get(i));
            }

            cookieStore.put("SRCHHPGUSR", sb.toString());
        }
    }

    private void initPageTelemetry(String html) {
        String ig = matchRegex("IG:\"([^\"]+)\"", html);
        String cid = matchRegex("CID:\"([^\"]+)\"", html);
        String da = matchRegex("DA:\"([^\"]+)\"", html);
        String salt = matchRegex("Salt:\"([^\"]+)\"", html);

        Random rand = new Random();
        int cw = rand.nextInt(61) + 1240;
        int ch = rand.nextInt(81) + 900;
        int scw = cw - 15;
        int sch = ch + rand.nextInt(401) + 700;
        int chc = ch - 14;

        fp = new HashMap<String, String>();
        fp.put("ig", ig);
        fp.put("cid", cid);
        fp.put("da", da);
        fp.put("salt", salt);
        fp.put("cw", String.valueOf(cw));
        fp.put("ch", String.valueOf(ch));
        fp.put("scw", String.valueOf(scw));
        fp.put("sch", String.valueOf(sch));
        fp.put("chc", String.valueOf(chc));

        Map<String, String> kv = new LinkedHashMap<String, String>();
        kv.put("CW", String.valueOf(cw));
        kv.put("CH", String.valueOf(ch));
        kv.put("SCW", String.valueOf(scw));
        kv.put("SCH", String.valueOf(sch));
        kv.put("DPR", "1.0");
        kv.put("UTC", String.valueOf(TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000));
        kv.put("PV", "10.0.0");
        kv.put("B", "0");
        kv.put("EXLTT", String.valueOf(rand.nextInt(21) + 20));
        kv.put("PRVCW", prevDims != null ? String.valueOf(prevDims[0]) : String.valueOf(cw));
        kv.put("PRVCH", prevDims != null ? String.valueOf(prevDims[1]) : String.valueOf(ch));
        kv.put("HV", String.valueOf(System.currentTimeMillis() / 1000));
        kv.put("HVE", salt);

        if (!cookieStore.containsKey("SRCHHPGUSR")) {
            kv.put("PREFCOL", "1");
            kv.put("BRW", "N");
            kv.put("BRH", "M");
        }

        updateSrchCookie(kv);
        prevDims = new int[]{cw, ch};
    }

    private void sendLs(String typ, String data, int dl, Map<String, String> extraParams, String queryUrl) {
        if (fp == null) return;
        try {
            StringBuilder urlBuilder = new StringBuilder("/fd/ls/l?");
            urlBuilder.append("IG=").append(fp.get("ig"));
            urlBuilder.append("&CID=").append(fp.get("cid"));
            urlBuilder.append("&Type=").append(typ);
            urlBuilder.append("&DATA=").append(encodeDataForLs(data));

            if (extraParams != null) {
                for (Map.Entry<String, String> entry : extraParams.entrySet()) {
                    urlBuilder.append("&").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            urlBuilder.append("&dl=").append(dl);

            ApiRequest finalLsReq = new ApiRequest("http://www.bing.com", urlBuilder.toString(), "GET");
            addStandardHeaders(finalLsReq);
            finalLsReq.addHeader("Referer", queryUrl);

            ApiResponse resp = executeWithRetry(finalLsReq);
            updateCookiesFromResponse(resp);
            closeResponseBody(resp);
        } catch (Exception e) {
            Log.e("Bing", "Error sending LS telemetry: " + e.getMessage());
        }
    }

    private void sendCpt(String flag, String queryUrl) {
        if (fp == null) return;
        Random rand = new Random();
        int fc = rand.nextInt(101) + 200;
        int bc = rand.nextInt(101) + 200;
        int h = rand.nextInt(21) + 240;
        int bp = rand.nextInt(21) + 250;
        int ct = rand.nextInt(31) + 250;
        int adLast = rand.nextInt(4);

        String d = "{\"pp\":{\"S\":\"" + flag + "\",\"FC\":" + fc + ",\"BC\":" + bc +
                ",\"SE\":-1,\"TC\":-1,\"H\":" + h + ",\"BP\":" + bp +
                ",\"CT\":" + ct + ",\"IL\":1},\"ad\":[-1,-1," + fp.get("scw") + "," +
                fp.get("chc") + "," + fp.get("scw") + "," + fp.get("sch") + "," + adLast + "],\"net\":\"undefined\"}";

        Map<String, String> cptParams = new HashMap<String, String>();
        cptParams.put("P", "SERP");
        cptParams.put("DA", fp.get("da"));

        sendLs("Event.CPT", d, 5, cptParams, queryUrl);
    }

    private void sendPowLog(String name, String info, String queryUrl) {
        String d = "[{\"T\":\"CI.Info\",\"FID\":\"CI\",\"Name\":\"PoW\",\"Text\":\"PoWChallengeSolver\",\"InfoData\":\"" + info + "\"}]";
        sendLs("Event.ClientInst", d, 4, null, queryUrl);
    }

    private void sendPerfV2(String queryUrl) {
        if (fp == null) return;
        try {
            long ts = System.currentTimeMillis();
            StringBuilder marks = new StringBuilder();
            Random rand = new Random();
            for (int i = 0; i < 15; i++) {
                if (i > 0) marks.append(",");
                marks.append(i).append(":").append(Integer.toHexString(rand.nextInt(401)));
            }
            String bodyData = "{" + marks.toString() + ",v:1.1,T:\"CI.Perf\",FID:\"CI\",Name:\"PerfV2\"}";
            String xmlBody = "<ClientInstRequest><Events><E><T>Event.ClientInst</T><IG>" + fp.get("ig")
                    + "</IG><TS>" + ts + "</TS><D><![CDATA[" + bodyData + "]]></D></E></Events><STS>" + ts + "</STS></ClientInstRequest>";

            ApiRequest request = new ApiRequest("http://www.bing.com", "/fd/ls/lsp.aspx?", "POST");
            request.setBody(xmlBody);
            addStandardHeaders(request);
            request.addHeader("Content-Type", "text/xml");
            request.addHeader("Referer", queryUrl);

            ApiResponse response = executeWithRetry(request);
            updateCookiesFromResponse(response);
            closeResponseBody(response);
        } catch (Exception e) {
            Log.e("Bing", "Error sending PerfV2 telemetry: " + e.getMessage());
        }
    }

    private static String encodeDataForLs(String data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '{' || c == '}' || c == '[' || c == ']' || c == ',' || c == ':' || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append("%20");
            } else {
                sb.append(String.format("%%%02X", (int) c));
            }
        }
        return sb.toString();
    }

    private static String matchRegex(String regex, String text) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private synchronized void updateCookiesFromResponse(ApiResponse response) {
        if (response == null || response.getHeaders() == null) return;

        Map<String, List<String>> headers = response.getHeaders();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                List<String> cookies = entry.getValue();
                if (cookies != null) {
                    for (int i = 0; i < cookies.size(); i++) {
                        parseAndStoreCookie(cookies.get(i));
                    }
                }
            }
        }
    }

    private void parseAndStoreCookie(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.length() == 0) return;
        String pair = cookieHeader.split(";")[0].trim();
        int eq = pair.indexOf('=');
        if (eq > 0) {
            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();

            if ("SRCHHPGUSR".equalsIgnoreCase(name)) {
                // Parse server key-value pairs and update via updateSrchCookie
                Map<String, String> kv = new HashMap<String, String>();
                String[] parts = value.split("&");
                for (String p : parts) {
                    if (p.length() > 0 && p.contains("=")) {
                        String[] k = p.split("=");
                        kv.put(k[0], k[1]);
                    }
                }
                updateSrchCookie(kv);
            } else {
                cookieStore.put(name, value);
            }
        }
    }

    private synchronized String getCookieHeader() {
        if (cookieStore.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : cookieStore.entrySet()) {
            if (!first) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static List<SearchResult> parse(String html) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        if (html == null || html.trim().length() == 0) {
            return results;
        }

        HtmlParser.Node root = HtmlParser.parseHtmlTree(html);
        List<HtmlParser.Node> algoNodes = new ArrayList<HtmlParser.Node>();
        findAlgoNodes(root, algoNodes);

        for (int i = 0; i < algoNodes.size(); i++) {
            HtmlParser.Node algoNode = algoNodes.get(i);
            String title = extractTitle(algoNode);
            String url = extractUrl(algoNode);
            String snippet = extractSnippet(algoNode);

            if (title.length() > 0 || url.length() > 0) {
                results.add(new SearchResult(title, url, snippet));
            }
        }

        return results;
    }

    private static void findAlgoNodes(HtmlParser.Node node, List<HtmlParser.Node> result) {
        if ("li".equals(node.tagName) && node.hasClass("b_algo")) {
            result.add(node);
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            findAlgoNodes(node.children.get(i), result);
        }
    }

    private static String extractTitle(HtmlParser.Node algoNode) {
        HtmlParser.Node h2Node = findNodeByTag(algoNode, "h2");
        if (h2Node != null) {
            StringBuilder sb = new StringBuilder();
            collectAllText(h2Node, sb);
            return cleanWhitespace(sb.toString());
        }
        return "";
    }

    private static String extractUrl(HtmlParser.Node algoNode) {
        HtmlParser.Node h2Node = findNodeByTag(algoNode, "h2");
        if (h2Node != null) {
            // Check inside <h2> for <a>
            HtmlParser.Node aNode = findNodeByTag(h2Node, "a");
            if (aNode != null) {
                String href = aNode.getAttribute("href");
                if (href != null && href.length() > 0) {
                    return href;
                }
            }
            // Check parent/ancestors of <h2> for <a>
            HtmlParser.Node parent = h2Node.parent;
            while (parent != null && parent != algoNode) {
                if ("a".equalsIgnoreCase(parent.tagName)) {
                    String href = parent.getAttribute("href");
                    if (href != null && href.length() > 0) {
                        return href;
                    }
                }
                parent = parent.parent;
            }
        }

        // Fallback: search for first <a> under b_algoheader or algoNode
        HtmlParser.Node headerNode = findNodeByClass(algoNode, "b_algoheader");
        HtmlParser.Node target = headerNode != null ? headerNode : algoNode;
        HtmlParser.Node fallbackA = findNodeByTag(target, "a");
        if (fallbackA != null) {
            String href = fallbackA.getAttribute("href");
            if (href != null && href.length() > 0) {
                return href;
            }
        }
        return "";
    }

    private static String extractSnippet(HtmlParser.Node algoNode) {
        StringBuilder sb = new StringBuilder();
        collectSnippetText(algoNode, sb);
        return cleanWhitespace(sb.toString());
    }

    private static void collectSnippetText(HtmlParser.Node node, StringBuilder sb) {
        if (isExcludedForSnippet(node)) {
            return;
        }
        if ("#text".equals(node.tagName)) {
            if (node.text != null) {
                sb.append(node.text).append(" ");
            }
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            collectSnippetText(node.children.get(i), sb);
        }
    }

    private static boolean isExcludedForSnippet(HtmlParser.Node node) {
        if (!node.isElement()) return false;

        String style = node.getAttribute("style");
        if (style != null) {
            String lowerStyle = style.toLowerCase();
            if (lowerStyle.contains("display:none") || lowerStyle.contains("visibility:hidden")) {
                return true;
            }
        }
        if ("true".equalsIgnoreCase(node.getAttribute("aria-hidden"))) {
            return true;
        }

        String cls = node.getAttribute("class");
        if (cls != null) {
            if (cls.contains("b_tpcn") ||
                    cls.contains("b_algoheader") ||
                    cls.contains("wiki_attr") ||
                    cls.contains("ansinfo") ||
                    cls.contains("b_hide") ||
                    cls.contains("b_wiki_see_more") ||
                    cls.contains("b_wikigbg_cmore") ||
                    cls.contains("expansionAccessibilityText") ||
                    cls.contains("ChevronDown12") ||
                    cls.contains("ChevronUp12") ||
                    cls.contains("b_mopexpref") ||
                    cls.contains("b_demoteText") ||
                    cls.contains("b_tranthis") ||
                    cls.contains("sw_up") ||
                    cls.contains("sw_down") ||
                    cls.contains("exp_img")) {
                return true;
            }
        }
        return false;
    }

    private static void collectAllText(HtmlParser.Node node, StringBuilder sb) {
        if ("#text".equals(node.tagName)) {
            if (node.text != null) {
                sb.append(node.text).append(" ");
            }
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            collectAllText(node.children.get(i), sb);
        }
    }

    private static HtmlParser.Node findNodeByClass(HtmlParser.Node node, String className) {
        if (node.hasClass(className)) return node;
        for (int i = 0; i < node.children.size(); i++) {
            HtmlParser.Node res = findNodeByClass(node.children.get(i), className);
            if (res != null) return res;
        }
        return null;
    }

    private static HtmlParser.Node findNodeByTag(HtmlParser.Node node, String tagName) {
        if (tagName.equalsIgnoreCase(node.tagName)) return node;
        for (int i = 0; i < node.children.size(); i++) {
            HtmlParser.Node res = findNodeByTag(node.children.get(i), tagName);
            if (res != null) return res;
        }
        return null;
    }

    private static String cleanWhitespace(String str) {
        if (str == null || str.length() == 0) return "";
        StringBuilder sb = new StringBuilder(str.length());
        boolean lastSpace = false;
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastSpace) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        return sb.toString().trim();
    }
}
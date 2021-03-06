package com.emc.ecs.monitoring.sample;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PutMetricsRequest {
    private static final Logger logger = LoggerFactory.getLogger(PutMetricsRequest.class);

    protected static byte[] sha256(String content) throws Exception { 
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         byte[] encodedhash = digest.digest(
                   content.getBytes(StandardCharsets.UTF_8));
         return encodedhash;
    }	
    protected static String bytesToHex(byte[] hash) {
    StringBuffer hexString = new StringBuffer();
    for (int i = 0; i < hash.length; i++) {
    String hex = Integer.toHexString(0xff & hash[i]);
    if(hex.length() == 1) hexString.append('0');
        hexString.append(hex);
    }
    return hexString.toString();
    }
    protected static byte[] HmacSHA256(String data, byte[] key) throws Exception {
        String algorithm="HmacSHA256";
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(data.getBytes("UTF8"));
    }

    protected static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes("UTF8");
        byte[] kDate = HmacSHA256(dateStamp, kSecret);
        byte[] kRegion = HmacSHA256(regionName, kDate);
        byte[] kService = HmacSHA256(serviceName, kRegion);
        byte[] kSigning = HmacSHA256("aws4_request", kService);
        return kSigning;
    }

    protected static Map<String, String> getHeaders(String amz_date, String authorization_header, String apiName, String content_type) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-amz-date", amz_date);
        headers.put("Authorization", authorization_header);
        headers.put("x-amz-target", "GraniteServiceVersion20100801."+apiName);
        headers.put("Content-Type", content_type);
        headers.put("Accept", "application/json");
        headers.put("Content-Encoding", "amz-1.0");
        headers.put("Connection", "keep-alive");
        return headers;
    }


    public static String getResponse(String httpsURL, Map<String, String> headers, String payload) throws Exception {
            URL myurl = new URL(httpsURL);
            String response = null;
            logger.info("Sending a post request to:"  + httpsURL);
            HttpsURLConnection con = (HttpsURLConnection)myurl.openConnection();
            con.setRequestMethod("POST");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                logger.info("Header "+entry.getKey()+": " + entry.getValue());
                con.setRequestProperty(entry.getKey(), entry.getValue());
            }
            con.setDoOutput(true);
            con.setDoInput(true);
            try (DataOutputStream output = new DataOutputStream(con.getOutputStream())) {
                output.writeBytes(payload);
            }
            try (DataInputStream input = new DataInputStream(con.getInputStream())) {
                StringBuffer contents = new StringBuffer();
                String tmp;
                while ((tmp = input.readLine()) != null) {
                    contents.append(tmp);
                    logger.debug("tmp="+tmp);
                }
                response = contents.toString();
            }
            logger.info("Resp Code:" + con.getResponseCode());
            logger.info("Resp Message:" + con.getResponseMessage());
            return response;
        }

    protected static String getDateString() {
        String dateString = null;
        try {
            Date dt = new Date();
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            dateString = dateFormatter.format(dt);
            logger.info("x_amz_date = "+dateString);
        } catch (Exception e) {
            logger.error("Exception:", e); 
        }
        return dateString;
    }
    public static void main(String[] args) throws InvalidKeyException, NoSuchAlgorithmException, IllegalStateException, UnsupportedEncodingException {
        String AWS_ACCESS_KEY_ID="my_aws_key_id";
        String AWS_SECRET_ACCESS_KEY="my_aws_secret_id";
        String service="monitoring";
        String host="monitoring.us-east-1.amazonaws.com";
        String region="us-east-1";
        String endpoint="https://monitoring.us-east-1.amazonaws.com";
        String AWS_request_parameters="Action=PutMetricData&Version=2010-08-01";
        String amz_date = getDateString(); 
        String date_stamp = amz_date.substring(0, amz_date.indexOf("T"));
        String canonical_uri = "/";
        String canonical_querystring = "";
        String method = "POST";
        String apiName = "PutMetricData";
        String content_type = "application/x-amz-json-1.0";
        String amz_target = "GraniteServiceVersion20100801."+apiName;
        String canonical_headers = "content-type:" + content_type + "\n" + "host:" + host + "\n" + "x-amz-date:" + amz_date + "\n" + "x-amz-target:" + amz_target + "\n";
        String signed_headers = "content-type;host;x-amz-date;x-amz-target";
        String accessKey = AWS_ACCESS_KEY_ID;
        String accessSecretKey = AWS_SECRET_ACCESS_KEY;
        String date = "20130806";
        String signing = "aws4_request";
        String request_parameters = "{";
        request_parameters += "\"Namespace\":\"On-PremiseObjectStorageMetrics\",";
        request_parameters += "\"MetricData\":";
        request_parameters += "[";
        request_parameters += "  {";
        request_parameters += "    \"MetricName\": \"NumberOfObjects1\",";
        request_parameters += "    \"Dimensions\": [";
        request_parameters += "      {";
        request_parameters += "        \"Name\": \"BucketName\",";
        request_parameters += "        \"Value\": \"ExampleBucket\"";
        request_parameters += "      },";
        request_parameters += "      {";
        request_parameters += "        \"Name\": \"ECSSystemId\",";
        request_parameters += "        \"Value\": \"UUID\"";
        request_parameters += "      }";
        request_parameters += "    ],";
        request_parameters += "    \"Timestamp\": " + null + ",";
        request_parameters += "    \"Value\": 10,";
        request_parameters += "    \"Unit\": \"Count\",";
        request_parameters += "    \"StorageResolution\": 60";
        request_parameters += "  }";
        request_parameters += "]";
        request_parameters += "}";
        request_parameters = new String(request_parameters.getBytes("UTF-8"), "UTF-8");

        try {
            String payload_hash = bytesToHex(sha256(request_parameters)); 
            String canonical_request = method + "\n" + canonical_uri + "\n" + canonical_querystring + "\n" + canonical_headers + "\n" + signed_headers + "\n" + payload_hash;
            canonical_request = new String(canonical_request.getBytes("UTF-8"), "UTF-8");
            String algorithm = "AWS4-HMAC-SHA256";
            String credential_scope = date_stamp + "/" + region + "/" + service + "/" + "aws4_request";
            String string_to_sign = algorithm + "\n" +  amz_date + "\n" +  credential_scope + "\n" +  bytesToHex(sha256(canonical_request));
            string_to_sign = new String(string_to_sign.getBytes("UTF-8"), "UTF-8");
            byte[] signing_key = getSignatureKey(accessSecretKey, date_stamp, region, service);
            String signature = bytesToHex(HmacSHA256(string_to_sign, signing_key));
            logger.info("signature: {}", bytesToHex(signing_key));
            String authorization_header = algorithm + " " + "Credential=" + accessKey + "/" + credential_scope + ", " +  "SignedHeaders=" + signed_headers + ", " + "Signature=" + signature;
            logger.info("authorization_header="+authorization_header);
            Map<String, String> headers = getHeaders(amz_date, authorization_header, apiName, content_type);
            logger.info("Sending request with:" + request_parameters);
            String response = getResponse(endpoint, headers, request_parameters);
            logger.info("response:"+response);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Exception:", e);
        }
    }
}
/*
output:
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - x_amz_date = 20181231T213344Z
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - signature: bfa7520029f34f6d407b381197bd18a97101efbd2d4fa5bc183c44522ce24fde
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - authorization_header=AWS4-HMAC-SHA256 Credential=obfuscated/20181231/us-east-1/monitoring/aws4_request, SignedHeaders=content-type;host;x-amz-date;x-amz-target, Signature=b08a51264237c6e92bf389c45f1ca536d3f7f57a8e9c43b2f724773bad7b6c97
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Sending request with:{"Namespace":"On-PremiseObjectStorageMetrics","MetricData":[  {    "MetricName": "NumberOfObjects",    "Dimensions": [      {        "Name": "BucketName",        "Value": "ExampleBucket"      }    ],    "Timestamp": null,    "Value": 10,    "Unit": "Count",    "StorageResolution": 60  }]}
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Sending a post request to:https://monitoring.us-east-1.amazonaws.com
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Header Authorization: AWS4-HMAC-SHA256 Credential=obfuscated/20181231/us-east-1/monitoring/aws4_request, SignedHeaders=content-type;host;x-amz-date;x-amz-target, Signature=b08a51264237c6e92bf389c45f1ca536d3f7f57a8e9c43b2f724773bad7b6c97
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Header x-amz-target: GraniteServiceVersion20100801.PutMetricData
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Header x-amz-date: 20181231T213344Z
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Header Accept: application/json
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Header Content-Encoding: amz-1.0
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Header Connection: keep-alive
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Header Content-Type: application/x-amz-json-1.0
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Resp Code:200
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - Resp Message:OK
[main] INFO com.emc.ecs.monitoring.sample.PutMetricsRequest - response:
*/

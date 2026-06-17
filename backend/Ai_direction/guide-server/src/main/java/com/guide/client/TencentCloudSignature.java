package com.guide.client;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public final class TencentCloudSignature {

    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    private TencentCloudSignature() {
    }

    public static String buildAuthorization(String secretId, String secretKey,
                                            String service, String host,
                                            String action, String version,
                                            String region, String payload,
                                            long unixTimestamp) {
        try {
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
            dateFmt.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date now = new Date(unixTimestamp * 1000);
            String date = dateFmt.format(now);

            String ct = "application/json; charset=utf-8";
            String canonicalHeaders = "content-type:" + ct + "\nhost:" + host + "\n";
            String signedHeaders = "content-type;host";
            String hashedPayload = sha256Hex(payload);

            String canonicalRequest = "POST\n/\n\n"
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + hashedPayload;

            String credentialScope = date + "/" + service + "/tc3_request";
            String hashedCanonicalRequest = sha256Hex(canonicalRequest);
            String stringToSign = ALGORITHM + "\n"
                    + unixTimestamp + "\n"
                    + credentialScope + "\n"
                    + hashedCanonicalRequest;

            byte[] secretDate = hmac256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
            byte[] secretService = hmac256(secretDate, service);
            byte[] secretSigning = hmac256(secretService, "tc3_request");
            byte[] signature = hmac256(secretSigning, stringToSign);

            String signatureHex = bytesToHex(signature);

            return ALGORITHM + " "
                    + "Credential=" + secretId + "/" + credentialScope + ", "
                    + "SignedHeaders=" + signedHeaders + ", "
                    + "Signature=" + signatureHex;
        } catch (Exception e) {
            throw new RuntimeException("腾讯云签名生成失败", e);
        }
    }

    public static String buildTimestamp() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }

    private static byte[] hmac256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}

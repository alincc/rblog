package com.github.rmannibucau.rblog.social;

import com.github.rmannibucau.rblog.configuration.Configuration;
import com.github.rmannibucau.rblog.lang.JaxRsPromise;
import com.github.rmannibucau.rblog.service.URLService;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.joining;

@ApplicationScoped
public class TwitterService implements SocialService {
    @Inject
    @Configuration("rblog.twitter.token")
    private String token;

    @Inject
    @Configuration("rblog.twitter.tokenSecret")
    private String tokenSecret;

    @Inject
    @Configuration("rblog.twitter.consumerKey")
    private String consumerKey;

    @Inject
    @Configuration("rblog.twitter.consumerSecret")
    private String consumerSecret;

    @Inject
    @Configuration("${rblog.twitter.api.update.url:https://api.twitter.com/1.1/statuses/update.json}")
    private String updateEndpoint;

    @Inject
    private TimestampProvider timestampProvider;

    @Inject
    private NonceProvider nonceProvider;

    @Inject
    private URLService urlService;

    private WebTarget updateTarget;
    private byte[] signingKey;
    private String signingStringConstantPrefix;

    @PostConstruct
    private void prepareClient() {
        if (consumerSecret == null || tokenSecret == null) { // not activated
            return;
        }

        final Client client = ClientBuilder.newBuilder().build();
        updateTarget = client.target(updateEndpoint);

        signingKey = (urlService.percentEncode(consumerSecret) + '&' + urlService.percentEncode(tokenSecret)).getBytes(StandardCharsets.UTF_8);
        signingStringConstantPrefix = "POST&" + urlService.percentEncode(updateEndpoint) + "&";
    }

    @Override
    public boolean isActive() {
        return signingKey != null;
    }

    @Override
    public CompletableFuture<?> publish(final String message) {
        final JaxRsPromise jaxRsPromise = new JaxRsPromise();
        final Future<?> future = updateTarget.request()
            .accept(MediaType.APPLICATION_JSON_TYPE)
            .header("Authorization", buildAuthorizationHeader(singletonMap("status", message))).async()
            .post(Entity.entity("status=" + urlService.percentEncode(message), MediaType.APPLICATION_FORM_URLENCODED), jaxRsPromise.toJaxRsCallback());
        return jaxRsPromise.propagateCancel(future).toFuture();
    }

    private String buildAuthorizationHeader(final Map<String, String> params) {
        final Map<String, String> values = new TreeMap<>();
        values.put("oauth_consumer_key", consumerKey);
        values.put("oauth_nonce", nonceProvider.next());
        values.put("oauth_signature_method", "HMAC-SHA1");
        values.put("oauth_timestamp", Long.toString(timestampProvider.now()));
        values.put("oauth_token", token);
        values.put("oauth_version", "1.0");
        values.putAll(params);

        // encode
        values.entrySet().stream().forEach(e -> e.setValue(urlService.percentEncode(e.getValue())));

        final String signature = hmac(signingString(values));
        values.put("oauth_signature", signature);

        return "OAuth " + values.entrySet().stream()
            .filter(e -> e.getKey().startsWith("oauth_"))
            .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
            .collect(joining(", "));
    }

    private String hmac(final String signingString) {
        try {
            final SecretKeySpec key = new SecretKeySpec(signingKey, "HmacSHA1");
            final Mac mac = Mac.getInstance(key.getAlgorithm());
            mac.init(key);
            return urlService.percentEncode(Base64.getEncoder().encodeToString(mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8))));
        } catch (final InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String signingString(final Map<String, String> values) {
        return signingStringConstantPrefix + urlService.percentEncode(values.entrySet().stream().map(e -> String.format("%s=%s", e.getKey(), e.getValue())).collect(joining("&")));
    }

    public static class TimestampProvider { // in seconds not ms
        public long now() {
            return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        }
    }

    public static class NonceProvider { // whatever is unique per request
        public String next() {
            return UUID.randomUUID().toString();
        }
    }
}

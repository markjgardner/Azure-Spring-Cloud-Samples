/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure;

import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpResponse;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.fasterxml.jackson.core.*;
import com.nimbusds.oauth2.sdk.TokenRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

import java.net.*;
import java.io.*;
import java.time.Duration;
import java.util.Arrays;

@RestController
public class MainController {

    @Value("${azure.function.uri:local}")
    private String functionUri;
    private String triggerPath = "httptrigger";

    @GetMapping(path="/func/{name}")
    public String invokeFunction(@PathVariable String name) {
        try {
            TokenRequestContext context = new TokenRequestContext();
            context.addScopes(functionUri);

            RestTemplate restTemplate = new RestTemplate();
     
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(getMsiToken());
            HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);

            String requestUri = String.format("%s/api/%s?name=%s", functionUri, triggerPath, name);
     
            ResponseEntity<HttpResponse> result = restTemplate.exchange(requestUri, HttpMethod.GET, entity, HttpResponse.class);
                       
            return String.format("Function Response: %s", result.getBody());
        } catch (Exception ex) {
            return String.format("Failed to invoke function %s", functionUri);
        }
    }

    private String getMsiToken() {
        String requestUri = String.format("http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=%s",functionUri);

        try {
            URL msiEndpoint = new URL(requestUri);
            HttpURLConnection con = (HttpURLConnection) msiEndpoint.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Metadata", "true");
    
            if (con.getResponseCode()!=200) {
                throw new Exception("Error calling managed identity token endpoint.");
            }
    
            InputStream responseStream = con.getInputStream();
    
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createParser(responseStream);
    
            while(!parser.isClosed()){
                JsonToken jsonToken = parser.nextToken();
    
                if(JsonToken.FIELD_NAME.equals(jsonToken)){
                    String fieldName = parser.getCurrentName();
                    jsonToken = parser.nextToken();
    
                    if("access_token".equals(fieldName)){
                        return parser.getValueAsString();
                    }
                }
            }

            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}

package com.gnomon.substitution.services;

import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class appConfig {

        @Value("${kie.server.user}")
        private String user;

        @Value("${kie.server.pwd}")
        private String password;

        @Value("${kie.server.url}")
        private String url;

        @Bean
        public KieServicesClient kieServicesClient(
                @Value("${kie.server.url}") String kieRestEndpoint,
                @Value("${kie.server.user}") String kieUser,
                @Value("${kie.server.pwd}") String kiePassword,
                @Value("${EXECUTION_TIMEOUT:600000}") Integer executionTimeout) {
            KieServicesConfiguration conf = KieServicesFactory.newRestConfiguration(kieRestEndpoint, kieUser, kiePassword);
            conf.setMarshallingFormat(MarshallingFormat.JSON);
            conf.setTimeout(executionTimeout);
            return KieServicesFactory.newKieServicesClient(conf);
        }
    }



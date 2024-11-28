package io.fairspace.saturn.config;

import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.apache.jena.riot.RDFDataMgr.loadModel;

@Configuration
public class RdfModelConfig {

    @Bean
    public Model systemVocabulary() {
        return loadModel("system-vocabulary.ttl");
    }

    @Bean
    public Model userVocabulary() {
        return loadModel("vocabulary.ttl");
    }

    @Bean
    public Model vocabulary(
            @Qualifier("systemVocabulary") Model systemVocabulary, @Qualifier("userVocabulary") Model userVocabulary) {
        return systemVocabulary.union(userVocabulary);
    }
}

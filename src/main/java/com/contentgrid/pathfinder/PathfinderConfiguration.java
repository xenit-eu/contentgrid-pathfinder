package com.contentgrid.pathfinder;

import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE;
import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE;

import com.contentgrid.pathfinder.config.PathfinderProperties;
import com.contentgrid.pathfinder.kcontroller.ConfigMapWatcher;
import com.contentgrid.pathfinder.kcontroller.IngressGenerator;
import com.contentgrid.pathfinder.kcontroller.IngressManager;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@Configuration
@EnableConfigurationProperties({PathfinderProperties.class})
@Slf4j
public class PathfinderConfiguration {

    @Bean
    KubernetesClient kubernetesClient(PathfinderProperties properties) {
        var config = Optional.ofNullable(properties.getKubernetes())
                .orElseGet(() -> {
                    log.warn("Using autoconfiguration for kubernetes client");
                    return Config.autoConfigure(null);
                });
        return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
    }
    
    @Bean
    IngressGenerator ingressGenerator(PathfinderProperties properties) {
        return new IngressGenerator(properties.getTarget());
    }
    
    @Bean
    ConfigMapWatcher configMapWatcher(PathfinderProperties properties, KubernetesClient kubernetesClient) {
        return new ConfigMapWatcher(properties.getSource(), kubernetesClient);
    }

    @Bean
    IngressManager ingressManager(PathfinderProperties properties,IngressGenerator ingressGenerator, KubernetesClient kubernetesClient, ObservationRegistry observationRegistry) {
        return new IngressManager(properties.getTarget(), ingressGenerator, kubernetesClient, observationRegistry);
    }

    @Bean
    ApplicationRunner runner(ConfigMapWatcher watcher, IngressManager manager, ConfigurableApplicationContext applicationContext) {
        return new WatchRunner(watcher, manager, applicationContext);
    }

    @RequiredArgsConstructor
    private static class WatchRunner implements ApplicationRunner, DisposableBean {
        private final ConfigMapWatcher watcher;
        private final IngressManager ingressManager;
        private final ConfigurableApplicationContext applicationContext;

        private Disposable disposable;

        @Override
        public void destroy() {
            if(disposable != null) {
                disposable.dispose();
            }
        }

        @Override
        public void run(ApplicationArguments args) {
            var scheduler = Schedulers.newBoundedElastic(DEFAULT_BOUNDED_ELASTIC_SIZE, DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "kcontroller", 60, false);
            disposable = watcher.watch()
                    .subscribeOn(scheduler)
                    .doOnComplete(applicationContext::close)
                    .subscribe(ingressManager::handleEvent, error -> {
                        log.error("Controller failed", error);
                        scheduler.dispose();
                        applicationContext.close();
                    });
        }
    }

}

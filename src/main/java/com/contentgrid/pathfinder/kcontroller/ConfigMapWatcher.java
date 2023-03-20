package com.contentgrid.pathfinder.kcontroller;

import com.contentgrid.pathfinder.config.PathfinderProperties.PathfinderSourceProperties;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@RequiredArgsConstructor
@Slf4j
public class ConfigMapWatcher {
    private final PathfinderSourceProperties sourceProperties;
    private final KubernetesClient kubernetesClient;

    public Flux<Event> watch() {
        return Flux.create(emitter -> {
            var watchAdapter = new WatcherAdapter(emitter);

            var watch = kubernetesClient.configMaps()
                    .inNamespace(sourceProperties.getNamespace())
                    .withLabels(sourceProperties.getLabels())
                    .watch(watchAdapter);
            log.info("Watching configmaps in namespace '{}' with labels {}", sourceProperties.getNamespace(), sourceProperties.getLabels());

            emitter.onDispose(watch::close);
        });
    }

    @RequiredArgsConstructor
    private static class WatcherAdapter implements Watcher<ConfigMap> {
        private final FluxSink<Event> sink;

        @Override
        public void eventReceived(Action action, ConfigMap resource) {
            log.debug("Received event '{}' on configmap '{}'", action, resource);
            sink.next(new Event(action, resource));
        }

        @Override
        public void onClose(WatcherException cause) {
            sink.error(cause);
        }
    }
}

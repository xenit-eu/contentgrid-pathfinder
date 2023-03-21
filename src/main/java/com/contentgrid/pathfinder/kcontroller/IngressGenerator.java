package com.contentgrid.pathfinder.kcontroller;

import com.contentgrid.pathfinder.config.PathfinderProperties.PathfinderTargetProperties;
import com.contentgrid.pathfinder.config.PathfinderProperties.ServiceMappingProperties;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class IngressGenerator {
    private final PathfinderTargetProperties targetProperties;
    public static final String DOMAINS_FIELD_NAME = "contentgrid.routing.domains";
    public static final String SOURCE_CONFIGMAP = "pathfinder.contentgrid.com/source-cm";

    public Optional<Ingress> createIngress(ConfigMap configMap) {
        var domainNameString = configMap.getData().get(DOMAINS_FIELD_NAME);
        if(domainNameString == null) {
            log.warn("ConfigMap '{}' does not have any domainnames configured", configMap.getMetadata().getName());
            return Optional.empty();
        }
        if(targetProperties.getServices().isEmpty()) {
            log.warn("No services configured for target, can not generate an ingress");
            return Optional.empty();
        }
        var domainNames = List.of(domainNameString.split(","));


        var ingressBuilder = new IngressBuilder();
        var ingressTlsBuilder = new IngressTLSBuilder()
                .addAllToHosts(domainNames)
                .withSecretName("ingress-tls-"+configMap.getMetadata().getName());

        // Max size of TLS certificate CN field is 64 characters, add a fallback domain name that can be used as CN.
        if(ingressTlsBuilder.getHosts().stream().noneMatch(domainName -> domainName.length() < 64)) {
            if(targetProperties.getTls().getFallbackCnDomainName() != null) {
                ingressTlsBuilder.addToHosts(targetProperties.getTls().getFallbackCnDomainName());
            } else {
                log.warn("Ingress for ConfigMap '{}': all domainnames are longer than 64 characters. Set 'pathfinder.target.tls.fallback-cn-domain-name' to be able to generate a TLS certificate.", configMap.getMetadata().getName());
            }
        }

        // @formatter:off
        return Optional.of(ingressBuilder.editOrNewMetadata()
                    .withNamespace(targetProperties.getNamespace())
                    .withGenerateName(configMap.getMetadata().getName())
                    .addToLabels(configMap.getMetadata().getLabels())
                    .addToLabels(labelsFor(configMap))
                    .addToAnnotations(targetProperties.getAnnotations())
                .endMetadata()
                .withNewSpec()
                    .withRules(domainNames.stream().map(this::createIngressRule).toList())
                    .withTls(ingressTlsBuilder.build())
                .endSpec()
                .build());
        // @formatter:on
    }

    public Map<String, String> labelsFor(ConfigMap configMap) {
        return Map.of(SOURCE_CONFIGMAP, configMap.getMetadata().getName());
    }

    private IngressRule createIngressRule(String hostname) {
        // @formatter:off
        return new IngressRuleBuilder()
                .withHost(hostname)
                .withNewHttp()
                    .withPaths(targetProperties.getServices().stream().map(IngressGenerator::createIngressPath).toList())
                .endHttp()
                .build();
        // @formatter:on
    }

    private static HTTPIngressPath createIngressPath(ServiceMappingProperties serviceMapping) {
        // @formatter:off
        return new HTTPIngressPathBuilder()
                .withPath(serviceMapping.getPath())
                .withPathType(serviceMapping.getPathType())
                .withNewBackend()
                    .withNewService()
                        .withName(serviceMapping.getServiceName())
                        .withNewPort(serviceMapping.getServicePortName(), serviceMapping.getServicePort())
                    .endService()
                .endBackend()
                .build();
        // @formatter:on
    }

}

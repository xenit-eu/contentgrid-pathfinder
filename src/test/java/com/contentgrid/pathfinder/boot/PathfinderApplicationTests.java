package com.contentgrid.pathfinder.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.contentgrid.pathfinder.config.PathfinderProperties;
import com.contentgrid.pathfinder.kcontroller.IngressGenerator;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
		"pathfinder.target.services[0].path=/",
		"pathfinder.target.services[0].pathType=Prefix",
		"pathfinder.target.services[0].serviceName=test-123",
		"pathfinder.target.services[0].servicePort=123",
		"pathfinder.target.annotations.static-annotation=static-value",
		"pathfinder.target.copy-annotations.dynamic-annotation.default-value=dynamic-default",
		"pathfinder.target.copy-annotations.dynamic-annotation.acceptable-values=dynamic-default,dynamic-option1,dynamic-option2",
})
@Testcontainers
class PathfinderApplicationTests {
	@Container
	private final static K3sContainer k3sContainer = new K3sContainer(DockerImageName.parse("rancher/k3s:latest"));

	@DynamicPropertySource
	static void registerKubernetesProperties(DynamicPropertyRegistry registry) {
		var config = Config.fromKubeconfig(k3sContainer.getKubeConfigYaml());
		registry.add("pathfinder.kubernetes.masterUrl", config::getMasterUrl);
	}

	@TestConfiguration
	static class CustomKubernetesConfiguration {
		@Bean
		@Primary
		KubernetesClient testKubernetesClient() {
			var config = Config.fromKubeconfig(k3sContainer.getKubeConfigYaml());
			return new KubernetesClientBuilder()
					.withConfig(config)
					.build();
		}

	}

	@Autowired
	private KubernetesClient kubernetesClient;

	@Autowired
	private PathfinderProperties config;

	@BeforeAll
	static void setupNamespace() {
		var config = Config.fromKubeconfig(k3sContainer.getKubeConfigYaml());
		var client = new KubernetesClientBuilder()
				.withConfig(config)
				.build();

		client.namespaces().resource(new NamespaceBuilder()
				.withNewMetadata()
						.withName("contentgrid-system")
				.endMetadata()
				.build())
				.create();
	}


	@Test
	void configMapLifeCycle() {
		config.getTarget().setIngressClassName("my-ingress-class");

		var configMap = kubernetesClient.configMaps().inNamespace("default")
				.resource(new ConfigMapBuilder()
						.withNewMetadata()
						.withGenerateName("test-cm-")
						.addToLabels("app.contentgrid.com/service-type", "gateway")
						.addToLabels("app.contentgrid.com/application-id", "abc-def")
						.endMetadata()
						.addToData(IngressGenerator.DOMAINS_FIELD_NAME, "abc-def.example.invalid,fff.example.invalid")
						.build())
				.create();

		// Wait for ingress being created
		await().atMost(1, TimeUnit.SECONDS).until(() -> {
			return !kubernetesClient.network().v1().ingresses()
					.inNamespace("contentgrid-system")
					.withLabel(IngressGenerator.SOURCE_CONFIGMAP, configMap.getMetadata().getName())
					.list()
					.getItems()
					.isEmpty();
		});

		var ingresses = kubernetesClient.network().v1().ingresses()
				.inNamespace("contentgrid-system")
				.withLabel(IngressGenerator.SOURCE_CONFIGMAP, configMap.getMetadata().getName())
				.list()
				.getItems();

		assertThat(ingresses).singleElement()
				.satisfies(ingress -> {
					assertThat(ingress.getMetadata().getLabels())
							.containsKeys("app.contentgrid.com/application-id", "app.contentgrid.com/service-type");

					assertThat(ingress.getMetadata().getAnnotations())
							.containsEntry("static-annotation", "static-value")
							.containsEntry("dynamic-annotation", "dynamic-default");

					assertThat(ingress.getSpec().getIngressClassName()).isEqualTo("my-ingress-class");

					assertThat(ingress.getSpec().getRules())
							.map(IngressRule::getHost)
							.containsExactlyInAnyOrder("abc-def.example.invalid", "fff.example.invalid");
					assertThat(ingress.getSpec().getTls())
							.flatMap(IngressTLS::getHosts)
							.containsExactlyInAnyOrder("abc-def.example.invalid", "fff.example.invalid");
				});

		// Dynamic annotations are written back to the configmap
		assertThat(kubernetesClient.configMaps().resource(configMap).require()).satisfies(cm -> {
			assertThat(cm.getMetadata().getAnnotations())
					.containsEntry("dynamic-annotation", "dynamic-default");
		});

		// Reset the ingress class like it was unset
		config.getTarget().setIngressClassName(null);

		// Update the configmap
		kubernetesClient.configMaps()
				.inNamespace("default")
				.withName(configMap.getMetadata().getName())
				.edit(cm -> {
					cm.setData(Map.of(IngressGenerator.DOMAINS_FIELD_NAME, "abc-def.example.invalid"));
					return cm;
				});

		// Wait for ingress being updated
		await().atMost(1, TimeUnit.SECONDS).until(() -> {
			return !Objects.equals(kubernetesClient.network().v1().ingresses()
					.inNamespace("contentgrid-system")
					.withName(ingresses.get(0).getMetadata().getName())
					.get()
					.getMetadata()
					.getResourceVersion(), ingresses.get(0).getMetadata().getResourceVersion());
		});

		var ingress = kubernetesClient.network().v1().ingresses()
				.inNamespace("contentgrid-system")
				.withName(ingresses.get(0).getMetadata().getName())
				.get();

		// Ingress class should be maintained
		assertThat(ingress.getSpec().getIngressClassName()).isEqualTo("my-ingress-class");

		assertThat(ingress.getSpec().getRules())
				.map(IngressRule::getHost)
				.containsExactlyInAnyOrder("abc-def.example.invalid");

		// Delete the configmap
		kubernetesClient.configMaps()
				.inNamespace("default")
				.withName(configMap.getMetadata().getName())
				.delete();

		// Wait for ingress to be deleted
		await().atMost(1, TimeUnit.SECONDS).until(() -> {
			return kubernetesClient.network().v1().ingresses()
					.inNamespace("contentgrid-system")
					.withName(ingresses.get(0).getMetadata().getName())
					.get() == null;
		});
	}

}

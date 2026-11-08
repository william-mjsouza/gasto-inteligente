package com.gastointeligente.shared.web;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serve o build do frontend (copiado para {@code classpath:/static/} na imagem de distribuição).
 *
 * <p>Rotas da SPA sem extensão devolvem {@code index.html}. Caminhos sob {@code api/} e
 * {@code actuator/} nunca recebem o fallback: sem controlador correspondente, viram
 * {@code NoResourceFoundException} e são respondidos como JSON 404 por
 * {@link GlobalExceptionHandler}.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations("classpath:/static/")
			.resourceChain(true)
			.addResolver(new SpaFallbackResourceResolver());
	}

	static final class SpaFallbackResourceResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource resource = super.getResource(resourcePath, location);
			if (resource != null) {
				return resource;
			}
			if (!isSpaRoute(resourcePath)) {
				return null;
			}
			Resource index = location.createRelative("index.html");
			return (index.exists() && index.isReadable()) ? index : null;
		}

		static boolean isSpaRoute(String resourcePath) {
			String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
			if (path.equals("api") || path.startsWith("api/") || path.equals("actuator")
					|| path.startsWith("actuator/")) {
				return false;
			}
			String lastSegment = path.substring(path.lastIndexOf('/') + 1);
			return !lastSegment.contains(".");
		}

	}

}

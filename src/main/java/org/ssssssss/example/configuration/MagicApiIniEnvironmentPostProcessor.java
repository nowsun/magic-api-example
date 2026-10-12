package org.ssssssss.example.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class MagicApiIniEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	private static final String PROPERTY_SOURCE_NAME = "magicApiIni";
	private static final Path CONFIG_PATH = Paths.get("cfg", "magic-api.ini");

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (!Files.exists(CONFIG_PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			Map<String, Object> properties = readIni(reader);
			MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
			String applicationConfigSource = null;
			for (PropertySource<?> source : environment.getPropertySources()) {
				if (source.getName().startsWith("applicationConfig:")) {
					applicationConfigSource = source.getName();
					break;
				}
			}
			if (applicationConfigSource == null) {
				environment.getPropertySources().addLast(propertySource);
			} else {
				environment.getPropertySources().addBefore(applicationConfigSource, propertySource);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read " + CONFIG_PATH.toAbsolutePath(), e);
		}
	}

	static Map<String, Object> readIni(Reader reader) throws IOException {
		Map<String, Object> properties = new LinkedHashMap<>();
		String section = null;
		try (BufferedReader bufferedReader = new BufferedReader(reader)) {
			String line;
			int lineNumber = 0;
			while ((line = bufferedReader.readLine()) != null) {
				lineNumber++;
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
					continue;
				}
				if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
					section = trimmed.substring(1, trimmed.length() - 1).trim();
					if (section.isEmpty()) {
						throw invalidLine(lineNumber, "section name is empty");
					}
					continue;
				}
				int separator = trimmed.indexOf('=');
				if (section == null || separator <= 0) {
					throw invalidLine(lineNumber, "expected a [section] followed by key=value");
				}
				String key = trimmed.substring(0, separator).trim();
				String value = trimmed.substring(separator + 1).trim();
				if (key.isEmpty()) {
					throw invalidLine(lineNumber, "property name is empty");
				}
				String propertyName = section + "." + key;
				if (properties.put(propertyName, value) != null) {
					throw invalidLine(lineNumber, "duplicate property " + propertyName);
				}
			}
		}
		return properties;
	}

	private static IllegalArgumentException invalidLine(int lineNumber, String reason) {
		return new IllegalArgumentException("Invalid " + CONFIG_PATH + " at line " + lineNumber + ": " + reason);
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 11;
	}
}

package org.ssssssss.example.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 覆盖 knife4j 的 /doc.html，把 {@code <html lang=en>} 改为 zh-CN 并注入 notranslate 标记，
 * 避免浏览器每次打开文档页都弹“是否翻译为中文(简体)”。
 *
 * <p>knife4j 的 doc.html 由静态资源处理器提供（没有对应的 Controller），
 * 这里用优先级更高的 Controller 映射接管 /doc.html，运行时从 classpath 读取 knife4j
 * 原始页面再改写，因此升级 knife4j 后无需同步维护资源哈希。</p>
 */
@RestController
public class DocHtmlController {

	private static final String DOC_HTML_RESOURCE = "META-INF/resources/doc.html";

	@GetMapping(value = "/doc.html", produces = "text/html;charset=UTF-8")
	public String docHtml() throws IOException {
		byte[] bytes;
		try (InputStream in = new ClassPathResource(DOC_HTML_RESOURCE).getInputStream()) {
			bytes = FileCopyUtils.copyToByteArray(in);
		}
		return new String(bytes, StandardCharsets.UTF_8)
				.replace("<html lang=en>", "<html lang=\"zh-CN\" translate=\"no\">")
				.replace("<head><meta charset=utf-8>",
						"<head><meta name=\"google\" content=\"notranslate\"><meta charset=utf-8>");
	}
}

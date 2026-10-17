package org.ssssssss.example.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 覆盖 knife4j 的静态资源，修正其在中文环境下的两处展示问题：
 * <ul>
 *   <li>{@code /doc.html}：把 {@code <html lang=en>} 改为 zh-CN 并注入 notranslate，屏蔽浏览器翻译提示；</li>
 *   <li>{@code /webjars/js/app.*.js}：knife4j 前端把参数表第 6 列标题硬编码为英文
 *       {@code schema}（其余列为中文），运行时改写为「模型」。</li>
 * </ul>
 *
 * <p>knife4j 的 doc.html 与 app.js 均由静态资源处理器提供（没有对应 Controller），
 * 这里用优先级更高的 Controller 映射接管，运行时从 classpath 读取原始资源再改写，
 * 升级 knife4j 无需同步维护资源哈希（app.js 的路径是 3.0.3 构建哈希，升级时需同步该路径）。</p>
 */
@RestController
public class Knife4jUiController {

	private static final String DOC_HTML_RESOURCE = "META-INF/resources/doc.html";
	private static final String APP_JS_RESOURCE = "META-INF/resources/webjars/js/app.23f8b31d.js";

	@GetMapping(value = "/doc.html", produces = "text/html;charset=UTF-8")
	public String docHtml() throws IOException {
		return new String(readResource(DOC_HTML_RESOURCE), StandardCharsets.UTF_8)
				.replace("<html lang=en>", "<html lang=\"zh-CN\" translate=\"no\">")
				.replace("<head><meta charset=utf-8>",
						"<head><meta name=\"google\" content=\"notranslate\"><meta charset=utf-8>");
	}

	@GetMapping(value = "/webjars/js/app.23f8b31d.js", produces = "application/javascript;charset=UTF-8")
	public byte[] appJs() throws IOException {
		String js = new String(readResource(APP_JS_RESOURCE), StandardCharsets.UTF_8)
				.replace("title:\"schema\"", "title:\"模型\"")
				.replace("title: \"schema\"", "title: \"模型\"")
				.replace("| schema |", "| 模型 |")
				.replace("<th>schema</th>", "<th>模型</th>");
		return js.getBytes(StandardCharsets.UTF_8);
	}

	private byte[] readResource(String path) throws IOException {
		try (InputStream in = new ClassPathResource(path).getInputStream()) {
			return FileCopyUtils.copyToByteArray(in);
		}
	}
}

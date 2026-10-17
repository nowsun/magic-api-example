package org.ssssssss.example.configuration;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 美化 knife4j「响应示例」区域。
 *
 * <p>knife4j 3.0.3 会把每个字段的 {@code description}（也就是「参数说明」那一列的内容）
 * 注入到「响应示例」JSON 编辑器的右侧，作为灰底提示（{@code .knife4j-debug-editor-field-description}）。
 * 它和「参数说明」共用同一个 {@code description} 字段，knife4j 没有配置项能只关掉编辑器里的这份背景，
 * 所以这里通过在 {@code /doc.html} 注入一小段 CSS 把它隐藏掉，从而做到：
 * 「参数说明」列仍保留说明，「响应示例」只显示干净的 JSON 示例。</p>
 *
 * <p>另外，当接口的响应 schema 是 {@code type: object} 且没有 {@code properties}（例如 magic-api 动态接口
 * 未声明响应模型时），knife4j 仍会渲染一个只有表头、没有数据的「响应参数」空表格
 * （{@code .ant-table-empty}），看着不协调，这里连同它前面的「响应参数」标题一并隐藏。</p>
 *
 * <p>「响应示例」是 Ace 编辑器，默认会在第 80 列画一条 1px 的打印边距竖线（{@code .ace_print-margin}，
 * 位置指示线），只读 JSON 里用不上，也一并隐藏。</p>
 */
@Component
public class Knife4jHideFieldDescriptionFilter implements Filter {

    private static final String STYLE_TAG =
            "<style>"
                    + ".knife4j-debug-editor-field-description{display:none !important;}"
                    // 「响应参数」为空的接口（响应 schema 是 type:object 且没有 properties 时）
                    // 会渲染出一个只有表头、没有数据的空表格，隐藏它以及它前面的「响应参数」标题。
                    + ".ant-table-empty{display:none !important;}"
                    + ".api-title:has(+ .ant-table-wrapper .ant-table-empty){display:none !important;}"
                    // Ace 编辑器默认在第 80 列画一条 1px 竖线（print margin，打印边距/位置指示线），
                    // 在只读 JSON 里没有意义，藏掉。
                    + ".ace_print-margin{display:none !important;}"
                    + "</style>";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        if (uri == null || !uri.endsWith("/doc.html")) {
            chain.doFilter(request, response);
            return;
        }

        CapturingResponseWrapper wrapper = new CapturingResponseWrapper(httpResponse);
        chain.doFilter(request, wrapper);

        String html = new String(wrapper.getContent(), StandardCharsets.UTF_8);
        if (html.contains("</head>")) {
            html = html.replace("</head>", STYLE_TAG + "</head>");
        } else {
            html = STYLE_TAG + html;
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        httpResponse.setContentType("text/html;charset=UTF-8");
        httpResponse.setContentLength(bytes.length);
        httpResponse.getOutputStream().write(bytes);
    }

    private static class CapturingResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        CapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(int b) {
                    buffer.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    // no-op
                }
            };
        }

        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8));
        }

        byte[] getContent() {
            return buffer.toByteArray();
        }
    }
}

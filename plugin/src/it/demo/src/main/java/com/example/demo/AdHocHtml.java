package com.example.demo;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import com.example.demo.Wall.WallItem;
import com.example.demo.Wall.WallItems;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.template.soy.shared.SoyCssRenamingMap;

/**
 * A buggy equivalent for wall.soy.Wall.
 */
final class AdHocHtml {

 static void write(
     Map<String, ?> data, Nonce cspNonce, SoyCssRenamingMap cssRenamingMap,
     Writer out)
 throws IOException {
    out.write("<!doctype html>");
    out.write("<html>");
    out.write("<head>");
    out.write("<title>Wall</title>");
    for (Object style : (Iterable<?>) data.get("styles")) {
      String href = ((TrustedResourceUrl) style).getTrustedResourceUrlString();
      out.write("<link rel=\"stylesheet\" href=\"" + href + "\" />");
    }
    for (Object script : (Iterable<?>) data.get("scripts")) {
      String src = ((TrustedResourceUrl) script).getTrustedResourceUrlString();
      out.write("<script src=\"" + src + "\""
                + " nonce=\"" + cspNonce.text + "\" nonce=\"" + cspNonce.text
                + "\"></script>");
    }
    out.write("</head>");
    out.write("<body data-wall-version=\"" + data.get("version") + "\">");
    out.write("<ul class=\"" + cssRenamingMap.get("wall") + "\">");
    WallItems wallItems = (WallItems) data.get("wall");
    for (WallItem item : wallItems.getItemList()) {
      out.write("<li class=\"" + cssRenamingMap.get("wall-item") + "\""
                + " style=\"/*" + cspNonce.text + "*/"
                + "left: " + item.getCentroid().getXPercent() + "%;"
                + " top:  " + item.getCentroid().getYPercent() + "%\">"
                + item.getHtmlUntrusted()  // Intentional XSS Vulnerability
                + "</li>");
    }
    out.write("<li id=\"chip\" class=\"" + cssRenamingMap.get("blank") + "\">");
    out.write("</li>");
    out.write("</ul>");
    out.write("<form id=\"scribble\" action=\"#\""
              + " onsubmit=\"/*" + cspNonce.text + "*/return false\">");
    out.write("<table align=center>");
    out.write("<tr>");
    out.write("<th colspan=2>Enter some HTML<tr>");
    out.write("<td>");
    out.write("<textarea id=\"html\" rows=6 cols=60>"
              + "Hello, &lt;b &gt;&lt;span style=color:blue  &gt;"
              + "W&lt;/span &gt;&lt;span style=color:green &gt;o&lt;/span &gt;"
              + "&lt;span style=color:yellow&gt;r&lt;/span &gt;"
              + "&lt;span style=color:orange&gt;l&lt;/span &gt;"
              + "&lt;span style=color:red    &gt;d&lt;/span&gt;"
              + "&lt;/b &gt;&lt;span style=color:purple &gt;"
              + "!&lt;/span&gt; &#9786;</textarea>");
    out.write("<td rowspan=2>");
    out.write("<input type=range min=0 max=100 value=50 step=0.5 id=y"
              + " orient=vertical>");
    out.write("<tr>");
    out.write("<td>");
    out.write("<input type=range min=0 max=100 value=50 step=0.5 id=x>");
    out.write("<br>");
    out.write("<button id=\"scribble-submit\" type=\"button\">"
              + "Scribble</button>");
    out.write("</table>");
    out.write("</form>");
    out.write("</body>");
    out.write("</html>");
  }
}

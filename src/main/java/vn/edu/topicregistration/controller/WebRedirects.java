package vn.edu.topicregistration.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.view.RedirectView;

public final class WebRedirects {
    private WebRedirects() {
    }

    public static RedirectView seeOther(String localPath) {
        if (localPath == null || !localPath.startsWith("/") || localPath.startsWith("//")) {
            throw new IllegalArgumentException("Redirect path must be local");
        }
        RedirectView redirect = new RedirectView(localPath);
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        return redirect;
    }
}

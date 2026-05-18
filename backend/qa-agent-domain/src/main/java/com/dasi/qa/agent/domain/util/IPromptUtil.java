package com.dasi.qa.agent.domain.util;

import java.io.IOException;

public interface IPromptUtil {

    String loadSupervisorPrompt();

    String loadWebSearchPrompt();

    String loadRewriterPrompt();

    String loadPrompt(String path) throws IOException;

}

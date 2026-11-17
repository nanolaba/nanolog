package com.nanolaba.logging.examples;

import com.nanolaba.logging.LOG;

/**
 * Payloads such as LLM prompts, logged as their length and hash. The region is imported into the README.
 */
public class Payloads {

    void ask(String model, String prompt, String answer) {
        // nrg:begin:content
        LOG.debug("Prompt to {}: {}", model, LOG.content(prompt));
        // by default:                     Prompt to gpt-x: [content 1834 chars #3f2a9c1b]
        // -Dnanolog.content=truncate:20:  Prompt to gpt-x: You are a helpful as... [+1814 chars]
        // -Dnanolog.content=show:         Prompt to gpt-x: You are a helpful assistant.\nAnswer briefly. ...

        LOG.info("Answer received", LOG.kv("answer", LOG.content(answer)), LOG.kv("tokens", 812));
        // JSON: "fields":{"answer":{"content_len":5120,"content_hash":"9c41d2e7"},"tokens":812}
        // nrg:end:content
    }
}

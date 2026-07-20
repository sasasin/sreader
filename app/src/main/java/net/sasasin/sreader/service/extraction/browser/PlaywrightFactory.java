package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Playwright;

/** Creates Playwright process instances; production default is {@link Playwright#create()}. */
@FunctionalInterface
interface PlaywrightFactory {

  Playwright create();
}

package com.jpmorgan.cakeshop.config;

import com.jpmorgan.cakeshop.util.FileUtils;
import com.jpmorgan.cakeshop.util.StringUtils;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.jpmorgan.cakeshop")
@Profile("spring-boot")
public class SpringBootApplication {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBootApplication.class);

    public static void main(String[] args) throws IOException {
        // setup configs
        WebAppInit.setLoggingPath(true);

        String configDir = System.getProperty("cakeshop.config.dir");
        if (StringUtils.isBlank(configDir)) {
            configDir = FileUtils.expandPath(SystemUtils.USER_DIR, "data");
            LOG.info("No data directory provided, defaulting to {}", configDir);
            System.setProperty("cakeshop.config.dir", configDir);
        }
        LOG.info("cakeshop.config.dir=" + configDir);

        // extract binaries from WAR (if necessary)
        try {
            extractBinaries(configDir);
        } catch (IOException e) {
            System.err.println("!!! ERROR: Failed to extract binaries from WAR package");
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // boot app
        new SpringApplicationBuilder(SpringBootApplication.class)
                .profiles("container", "spring-boot")
                .bannerMode(Banner.Mode.OFF) // we manually display the banner once everything is ready
                .run(args);
    }

    private static void extractBinaries(String configDir) throws IOException {
        URL url = SpringBootApplication.class.getClassLoader().getResource("");
        String warUrl = null;

        if (url.getProtocol().equals("jar")) {
            warUrl = url.toString().replaceFirst("jar:", "");
            warUrl = warUrl.substring(0, warUrl.indexOf("!"));
        }

        URL newUrl = StringUtils.isNotBlank(warUrl) ? new URL(warUrl) : url;
        File war = FileUtils.toFile(newUrl);

        if (!war.toString().endsWith(".war")) {
            return; // no need to copy
        }

        String binRootDir = FileUtils.expandPath(configDir, "bin");
        System.out.println("Extracting binaries to " + binRootDir);

        try (ZipFile warZip = new ZipFile(war)) {
            Enumeration<? extends ZipEntry> entries = warZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String file = zipEntry.getName();
                if (zipEntry.isDirectory() || !file.startsWith("WEB-INF/classes/bin")) {
                    continue;
                }

                File target = new File(FileUtils.join(configDir, file.substring(16)));
                File targetDir = target.getParentFile();
                if (!targetDir.exists()) {
                    LOG.info("Creating bin directory: {}", targetDir.getAbsolutePath());
                    targetDir.mkdirs();
                }
                if(!target.exists()) {
                    LOG.info("Copying binary: {}", target.getAbsolutePath());
                    FileUtils.copyInputStreamToFile(warZip.getInputStream(zipEntry), target);
                }
            }
        }

        System.setProperty("eth.bin.dir", binRootDir);
    }

    @Bean
    @Profile("spring-boot")
    public ConfigurableServletWebServerFactory webServerFactory() {
      JettyServletWebServerFactory factory = new JettyServletWebServerFactory();
      factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/notfound.html"));
      return factory;
    }

}

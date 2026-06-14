package net.sasasin.sreader.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.scheduler.FeedReaderScheduler;
import net.sasasin.sreader.service.FeedTomlService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FeedReaderCommandRunner implements CommandLineRunner {

	private final FeedReaderProperties properties;
	private final FeedReaderScheduler scheduler;
	private final FeedTomlService feedTomlService;
	private final ConfigurableApplicationContext applicationContext;

	public FeedReaderCommandRunner(FeedReaderProperties properties,
			FeedReaderScheduler scheduler,
			FeedTomlService feedTomlService,
			ConfigurableApplicationContext applicationContext) {
		this.properties = properties;
		this.scheduler = scheduler;
		this.feedTomlService = feedTomlService;
		this.applicationContext = applicationContext;
	}

	@Override
	public void run(String... args) throws IOException {
		int feedsCommand = Arrays.asList(args).indexOf("feeds");
		if (feedsCommand >= 0) {
			runFeedsCommand(Arrays.copyOfRange(args, feedsCommand + 1, args.length));
			SpringApplication.exit(applicationContext, () -> 0);
		} else if (properties.job().runOnce()) {
			scheduler.runIfIdle();
			SpringApplication.exit(applicationContext, () -> 0);
		}
	}

	private void runFeedsCommand(String[] args) throws IOException {
		if (args.length == 0) {
			throw new IllegalArgumentException("feeds command requires export or import");
		}
		switch (args[0]) {
			case "export" -> exportFeeds(args);
			case "import" -> importFeeds(args);
			default -> throw new IllegalArgumentException("unsupported feeds command: " + args[0]);
		}
	}

	private void exportFeeds(String[] args) throws IOException {
		boolean activeOnly = hasFlag(args, "--active-only");
		String output = optionValue(args, "--output");
		String toml = feedTomlService.exportToml(activeOnly);
		if (output == null) {
			System.out.print(toml);
		} else {
			Files.writeString(Path.of(output), toml, StandardCharsets.UTF_8);
			System.out.println("Exported feeds to " + output);
		}
	}

	private void importFeeds(String[] args) throws IOException {
		String input = optionValue(args, "--input");
		if (input == null) {
			throw new IllegalArgumentException("feeds import requires --input <path>");
		}
		String toml = Files.readString(Path.of(input), StandardCharsets.UTF_8);
		FeedTomlService.ImportResult result = feedTomlService.importToml(
				toml,
				new FeedTomlService.ImportOptions(hasFlag(args, "--dry-run"), hasFlag(args, "--resubscribe")));
		System.out.println("Import result: inserted=" + result.inserted()
				+ ", updated=" + result.updated()
				+ ", unchanged=" + result.unchanged()
				+ ", unsubscribed=" + result.unsubscribed()
				+ ", resubscribed=" + result.resubscribed()
				+ ", conflicts=" + result.conflicts()
				+ ", errors=" + result.errors().size());
		for (String conflict : result.conflictMessages()) {
			System.out.println("Conflict: " + conflict);
		}
	}

	private boolean hasFlag(String[] args, String flag) {
		return Arrays.asList(args).contains(flag);
	}

	private String optionValue(String[] args, String option) {
		for (int i = 0; i < args.length - 1; i++) {
			if (option.equals(args[i])) {
				return args[i + 1];
			}
		}
		return null;
	}
}

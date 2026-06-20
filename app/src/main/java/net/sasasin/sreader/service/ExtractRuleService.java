package net.sasasin.sreader.service;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import net.sasasin.sreader.domain.ExtractRule;
import net.sasasin.sreader.repository.ExtractRuleRepository;
import org.springframework.stereotype.Service;

@Service
public class ExtractRuleService {

  private final ExtractRuleRepository extractRuleRepository;

  public ExtractRuleService(ExtractRuleRepository extractRuleRepository) {
    this.extractRuleRepository = extractRuleRepository;
  }

  public Optional<ExtractRule> findBestRule(String url) {
    return extractRuleRepository.findAll().stream()
        .filter(rule -> matches(url, rule.urlPattern()))
        .max(Comparator.comparingInt(rule -> rule.urlPattern().length()));
  }

  private boolean matches(String url, String pattern) {
    try {
      return url.matches(pattern + ".*");
    } catch (PatternSyntaxException e) {
      return false;
    }
  }
}

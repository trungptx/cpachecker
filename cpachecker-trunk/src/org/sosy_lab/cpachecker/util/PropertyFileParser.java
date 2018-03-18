/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.util;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.util.SpecificationProperty.PropertyType;

/**
 * A simple class that reads a property, i.e. basically an entry function and a proposition, from a given property,
 * and maps the proposition to a file from where to read the specification automaton.
 */
public class PropertyFileParser {

  public static class InvalidPropertyFileException extends Exception {

    private static final long serialVersionUID = -5880923544560903123L;

    public InvalidPropertyFileException(String msg) {
      super(msg);
    }

    public InvalidPropertyFileException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  private final Path propertyFile;

  private String entryFunction;
  private final Set<PropertyType> properties = Sets.newHashSetWithExpectedSize(1);

  private static final Pattern PROPERTY_PATTERN =
      Pattern.compile("CHECK\\( init\\((" + CFACreator.VALID_C_FUNCTION_NAME_PATTERN + ")\\(\\)\\), LTL\\((.+)\\) \\)");

  private static Map<String, PropertyType> AVAILABLE_PROPERTIES =
      Maps.<String, PropertyType>uniqueIndex(
          EnumSet.allOf(PropertyType.class), PropertyType::toString);

  public PropertyFileParser(final Path pPropertyFile) {
    propertyFile = pPropertyFile;
  }

  public void parse() throws InvalidPropertyFileException, IOException {
    String rawProperty = null;
    try (BufferedReader br = Files.newBufferedReader(propertyFile, Charset.defaultCharset())) {
      while ((rawProperty = br.readLine()) != null) {
        if (!rawProperty.isEmpty()) {
          properties.add(parsePropertyLine(rawProperty));
        }
      }
    }
    if (properties.isEmpty()) {
      throw new InvalidPropertyFileException("No property in file.");
    }
  }

  private PropertyType parsePropertyLine(String rawProperty) throws InvalidPropertyFileException {
    Matcher matcher = PROPERTY_PATTERN.matcher(rawProperty);

    if (rawProperty == null || !matcher.matches() || matcher.groupCount() != 2) {
      throw new InvalidPropertyFileException(
          String.format("The property '%s' is not well-formed!", rawProperty));
    }

    if (entryFunction == null) {
      entryFunction = matcher.group(1);
    } else if (!entryFunction.equals(matcher.group(1))) {
      throw new InvalidPropertyFileException(String.format(
          "Specifying two different entry functions %s and %s is not supported.", entryFunction, matcher.group(1)));
    }

    PropertyType propertyType = AVAILABLE_PROPERTIES.get(matcher.group(2));
    if (propertyType == null) {
      throw new InvalidPropertyFileException(String.format(
          "The property '%s' is not supported.", matcher.group(2)));
    }
    return propertyType;
  }

  public String getEntryFunction() {
    return entryFunction;
  }

  public Set<PropertyType> getProperties() {
    return Collections.unmodifiableSet(properties);
  }
}
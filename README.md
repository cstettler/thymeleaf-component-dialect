# Thymeleaf Component Dialect

A dialect for Thymeleaf that enables reusable UI components with named slots and parameters.

[![Maven Central](https://img.shields.io/maven-central/v/ch.cstettler.thymeleaf/thymeleaf-component-dialect.svg)](https://central.sonatype.com/artifact/ch.cstettler.thymeleaf/thymeleaf-component-dialect)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Open Issues](https://img.shields.io/github/issues/cstettler/thymeleaf-component-dialect.svg)](https://github.com/cstettler/thymeleaf-component-dialect/issues)
[![Build Status](https://github.com/cstettler/thymeleaf-component-dialect/actions/workflows/build.yml/badge.svg)](https://github.com/cstettler/thymeleaf-component-dialect/actions/workflows/build.yml)

## Features

- Define reusable UI components
- Support for default and named slots
- Support for component parameters
- Simple integration with Thymeleaf into Spring

## Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>ch.cstettler.thymeleaf</groupId>
    <artifactId>thymeleaf-component-dialect</artifactId>
    <version><!-- place version number here --></version>
</dependency>

<!-- Thymeleaf is a provided dependency -->
<dependency>
    <groupId>org.thymeleaf</groupId>
    <artifactId>thymeleaf</artifactId>
    <version>3.1.3.RELEASE</version>
</dependency>
```

## Usage

### 1. Register the dialect with Thymeleaf

When using Spring, define the component dialect as a bean in your application configuration and list your components via the `addComponent()` method:

```java
@Bean
public ComponentDialect componentDialect() {
    return new ComponentDialect()
        .addComponent("button", "components/button.html")
        .addComponent("alert", "components/alert.html")
        .addComponent("card", "components/card.html");
}
```
The component name is the tag name to use in your templates, and the template path is the location of the component template.
Template paths are relative to the `src/main/resources/templates` directory.

When directly instantiating the template engine, set the component dialect using `TemplateEngine.addDialect()`.

### 2. Create a component template

```html
<!-- src/main/resources/templates/components/card.html -->
<th:block xmlns:th="http://www.thymeleaf.org" th:fragment="card(title)">
  <div class="card">
    <div class="card-header" th:text="${title}">Card Title</div>
    <div class="card-body">
      <pl:slot>
        <!-- default slot -->
        <div>Default content goes here</div>
      </pl:slot>
      <pl:slot pl:name="footer">
        <!-- named slot -->
        <div>Footer content goes here</div>
      </pl:slot>
    </div>
  </div>
</th:block>
```

Do the same accordingly for the other components.

### 3. Use the component in your templates

```html
<pl:card pl:title="My Card">
  <div>This will replace the default slot content</div>

  <div pl:slot="footer">
    <b>This will replace the footer slot content</b>
  </div>
</pl:card>
```

## License

Thymeleaf Component Dialect is Open Source software released under the
[Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).
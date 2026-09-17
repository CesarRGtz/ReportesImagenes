package com.teosa.app.prototipo.domain;
public enum LaunchMode {
 REPORTS("Reportes fotográficos TEOSA",AppConfig.Role.SECONDARY),
 QUOTATIONS("Cotizaciones TEOSA",AppConfig.Role.SECONDARY),
 SERVER("Servidor TEOSA",AppConfig.Role.PRIMARY);
 private final String title;private final AppConfig.Role role;
 LaunchMode(String title,AppConfig.Role role){this.title=title;this.role=role;}
 public String title(){return title;}public AppConfig.Role role(){return role;}
}

package com.teosa.app.prototipo.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
public final class Quotation {
 public static final List<String> PAYMENT_OPTIONS=List.of("CONTADO","A 30 DIAS","A CONVENIR","A CREDITO");
 private boolean automaticFolio,folioAssigned;
 public boolean isAutomaticFolio(){return automaticFolio;} public void setAutomaticFolio(boolean value){automaticFolio=value;}
 public boolean isFolioAssigned(){return folioAssigned;} public void setFolioAssigned(boolean value){folioAssigned=value;}
 private Map<String,String> values=new LinkedHashMap<>();
 private List<QuotationLine> lines=new ArrayList<>();
 private String introduction="Atendiendo su amable solicitud estamos enviando cotización de los servicios requeridos, para nosotros es un placer poner nuestra compañía a su servicio.";
 private String notes="Cotización válida por 15 días";
 private BigDecimal taxRate=new BigDecimal("0.16");
 public Quotation(){values.put("fecha",LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));values.put("pago","CONTADO");}
 public Map<String,String> getValues(){return values;}
 public String value(String key){return values.getOrDefault(key,"");}
 public void setValue(String key,String value){values.put(key,value==null?"":value);}
 public List<QuotationLine> getLines(){return lines;}
 public String getIntroduction(){return introduction;} public void setIntroduction(String v){introduction=v;}
 public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
 public BigDecimal getTaxRate(){return taxRate;} public void setTaxRate(BigDecimal v){taxRate=v;}
 public BigDecimal subtotal(){return lines.stream().map(QuotationLine::amount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);}
 public BigDecimal tax(){return subtotal().multiply(taxRate).setScale(2,RoundingMode.HALF_UP);}
 public BigDecimal total(){return subtotal().add(tax());}
 public void validate(){
  if(value("cliente").isBlank())throw new IllegalArgumentException("Captura el cliente.");
  if(value("folio").isBlank())throw new IllegalArgumentException("Captura el folio de cotización.");
  if(!PAYMENT_OPTIONS.contains(value("pago")))throw new IllegalArgumentException("Selecciona una condición de pago válida.");
  if(taxRate==null || taxRate.signum()<0 || taxRate.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("El IVA debe estar entre 0 y 100%.");
  if(lines.isEmpty())throw new IllegalArgumentException("Agrega al menos una partida.");
  for(QuotationLine l:lines){
   if(l.getDescription()==null || l.getDescription().isBlank())throw new IllegalArgumentException("Cada partida necesita descripción.");
   if(l.getQuantity()==null || l.getQuantity().signum()<=0 || l.getQuantity().stripTrailingZeros().scale()>0)throw new IllegalArgumentException("La cantidad debe ser un entero mayor que cero.");
   for(QuotationScope scope:l.getScopes()){
    if(scope.getDescription().isBlank())throw new IllegalArgumentException("Cada alcance necesita descripción.");
    if(scope.getCost().signum()<0)throw new IllegalArgumentException("El costo del alcance no puede ser negativo.");
   }
   if(l.isScopeBreakdown() && l.getScopes().isEmpty())throw new IllegalArgumentException("Agrega alcances antes de desglosar el costo.");
   if(l.getUnitPrice()==null || l.getUnitPrice().signum()<0)throw new IllegalArgumentException("El costo unitario no puede ser negativo.");
  }
 }
}

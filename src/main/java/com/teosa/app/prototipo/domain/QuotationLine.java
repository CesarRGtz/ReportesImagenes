package com.teosa.app.prototipo.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
public final class QuotationLine {
 private java.util.List<QuotationScope> scopes;
 private boolean scopeBreakdown;
 private String code="", description="", scope="";
 private BigDecimal quantity=BigDecimal.ONE, unitPrice=BigDecimal.ZERO;
 public String getCode(){return code;} public void setCode(String v){code=v;}
 public String getDescription(){return description;} public void setDescription(String v){description=v;}
 public java.util.List<QuotationScope> getScopes(){
  if(scopes==null){scopes=new java.util.ArrayList<>();if(scope!=null)scope.lines().filter(s->!s.isBlank()).forEach(s->scopes.add(new QuotationScope(s)));}
  return scopes;
 }
 public boolean isScopeBreakdown(){return scopeBreakdown;}
 public void setScopeBreakdown(boolean enabled){if(scopeBreakdown&&!enabled)unitPrice=getUnitPrice();scopeBreakdown=enabled;}
 public String getScope(){return getScopes().stream().map(QuotationScope::getDescription).collect(java.util.stream.Collectors.joining("\n"));}
 public void setScope(String v){scope=v;scopes=null;}
 public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;}
 public BigDecimal getUnitPrice(){return scopeBreakdown?getScopes().stream().map(QuotationScope::getCost).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP):unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
 public BigDecimal amount(){return quantity.multiply(getUnitPrice()).setScale(2,RoundingMode.HALF_UP);}
}

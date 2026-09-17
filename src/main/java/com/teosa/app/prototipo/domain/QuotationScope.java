package com.teosa.app.prototipo.domain;
import java.math.*;
public final class QuotationScope {
 private String description="";private BigDecimal cost=BigDecimal.ZERO;
 public QuotationScope(){}
 public QuotationScope(String value){description=value;}
 public String getDescription(){return description==null?"":description;}public void setDescription(String value){description=value;}
 public BigDecimal getCost(){return cost==null?BigDecimal.ZERO:cost;}public void setCost(BigDecimal value){cost=value.setScale(2,RoundingMode.HALF_UP);}
 public static String code(int index){return String.format("A%02d",index+1);}
}

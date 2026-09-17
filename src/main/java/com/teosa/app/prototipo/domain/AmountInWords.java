package com.teosa.app.prototipo.domain;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
public final class AmountInWords {
 private static final String[] SMALL={"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciséis","diecisiete","dieciocho","diecinueve","veinte","veintiuno","veintidós","veintitrés","veinticuatro","veinticinco","veintiséis","veintisiete","veintiocho","veintinueve"};
 private static final String[] TENS={"","","","treinta","cuarenta","cincuenta","sesenta","setenta","ochenta","noventa"};
 private static final String[] HUNDREDS={"","ciento","doscientos","trescientos","cuatrocientos","quinientos","seiscientos","setecientos","ochocientos","novecientos"};
 public static String pesos(BigDecimal value){
  BigDecimal v=value.setScale(2,RoundingMode.HALF_UP);
  if(v.signum()<0 || v.compareTo(new BigDecimal("1000000000000"))>=0)throw new IllegalArgumentException("Importe fuera de rango para cantidad con letra.");
  long n=v.longValue(); int cents=v.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
  return (apocope(words(n))+(n>0 && n%1000000==0?" de":"")+(n==1?" peso ":" pesos ")+String.format(Locale.ROOT,"%02d/100 M.N.",cents)).toUpperCase(Locale.ROOT);
 }
 private static String apocope(String s){return s.endsWith("veintiuno")?s.substring(0,s.length()-9)+"veintiún":s.endsWith("uno")?s.substring(0,s.length()-3)+"un":s;}
 private static String words(long n){
  if(n<30)return SMALL[(int)n];
  if(n<100)return TENS[(int)n/10]+(n%10==0?"":" y "+words(n%10));
  if(n==100)return "cien";
  if(n<1000)return HUNDREDS[(int)n/100]+(n%100==0?"":" "+words(n%100));
  if(n<1000000)return (n/1000==1?"mil":apocope(words(n/1000))+" mil")+(n%1000==0?"":" "+words(n%1000));
  return (n/1000000==1?"un millón":apocope(words(n/1000000))+" millones")+(n%1000000==0?"":" "+words(n%1000000));
 }
}

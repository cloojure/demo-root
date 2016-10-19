package mypkg;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

class Main {
  public static void main( String[] args ) {
    System.out.println( "Hello from java" );

    IFn plus = Clojure.var("clojure.core", "+");
    System.out.println( "result: " + plus.invoke(1, 2) );
  }
}

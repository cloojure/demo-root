package mypkg;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

class Main {
  public static void main( String[] args ) {
    System.out.println( "Java Main.main()" );

    // clojure.core is automatically "required"; you don't need to
    IFn plus = Clojure.var("clojure.core", "+");
    System.out.println( "  plus: " + plus.invoke(1, 2) );

    // any other namespace needs to be "required"
    IFn require = Clojure.var("clojure.core", "require");
    require.invoke(Clojure.read("embedded-clojure.core"));

    IFn add  = Clojure.var("embedded-clojure.core", "add");
    System.out.println( "  add:  " +  add.invoke(2, 3) );
  }
}

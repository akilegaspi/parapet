# Parapet - purely functional library to develop distributed and event driven systems

##  Key features:

* Purely functional library written in scala using Tagless-Final Style and Free Monads thoughtfully designed for people who prefer functional style over imperative
* Rich DSL that allows to write composable and reusable code
* Lightweight and Performat. Library utilizes resources (CPU and Memory) in a smart way, the code is optimized to reduce CPU consumption when your application in idle state
* Built-in support for Cats Effect library
* Extendable. Library can be easily extended to support other Effect System libraries such as Scalaz Task, Monix and etc.

## Getting started

The first thing you need to do is to add *parapet-core* library into your project. You can find  the latest version in maven central:

```scala
libraryDependencies += "io.parapet" %% "core" % "0.0.1-RC1"
```

Once you added the library can start writing your first program, however it's worth to take a few minutes and get familiar with two main approaches to write processes: generic and effect specific, I'll describe both in a minute. For those who aren't familiar with effect systems like Cats Effect I'd strongly recommend you to read some articles about IO monad. Fortunately you don't need to be an expert in Cats Effect in order to use Parapet.

The first aproach we'll consider is Generic. It's recommended to stick to this style when writing processes. Let's develop a simple printer process that will print users requests to the system output.

```scala
import io.parapet.core.{Event, Process}

class Printer[F[_]] extends Process[F] {

  import Printer._ //  import Printer API

  import dsl._ // import DSL operations

  override def handle: Receive = {
    case Print(data) => eval(println(data))
  }
}

object Printer {

  case class Print(data: Any) extends Event

}
```

Let's walk through this code. You start writing your processes by extending `Process` trait and parameterizing it with an effect type. In this example we left so called hole `F[_]` in our `Printer` type which can be any type constructor with a single argument, e.g. `F[_]` is a generic type constructor, cats effect `IO` is a specific type constructor and `IO[Unit]` is a concrete type. Starting from this moment it should become clear what it means for a process to be generic. Simply speaking it means that a process doesn't depend on any specific effect type e.g. `IO`. Thus we can claim that our `Printer` process is surely a generic process. The next step is to define a process API or contract that defines set of events that it can send and recieve. Process contract is an important part of any process specification that should be taken seriously. API defines a protocol that other processes will use in order to communicate with your process. Please remember that it's very important aspect of any process definition and take it seriously. The next step would importing `DSL`, Parapet DSL is a small set of operations that we will consider in details in the next chapters, in this example we need only `eval` operator that  suspends a side effect in `F`, in our Printer process we suspend `println` effectful computation. Finally every process should override `handle` function defined in `Process` trait. `handle` function is a partial function that matches input events and produces an executable `flows`. If you ever tried Akka framework you may find this approach familiar (for the curious, `Receive` is simply a type alias for `PartialFunction[Event, DslF[F, Unit]]`). In our Printer process we match on `Print` event using well known pattern-matching feature in Scala language. If you are new in functional programming I'd strongly recommend to read about pattern-matching, it's a very powerfull feature. 
That's basically it, we consider every important  aspect of our Printer process, let's move forward and write a simple client process that will talk to out Printer.


```scala
import io.parapet.core.Event.Start
import io.parapet.core.{Process, ProcessRef}
import io.parapet.examples.Printer._ // import Printer API

class PrinterClient[F[_]](printer: ProcessRef) extends Process[F] {
  override def handle: Receive = {
    // Start is a lifecycle event that gets delivered when a process started
    case Start => Print("hello world") ~> printer
  }
}
```

As you already might noticed we are repeating the same steps we made when were writing our `Printer` process: 
* Create a new Process with a  _hole_ `F[_]` in it's type definition
* Extend `io.parapet.core.Process` trait and parametrizing it with generic effect type `F`
* Implement `handle` partial function

Let's consider some new types and operators we have used to write our client: `ProcessRef`, `Start` lifecycle event and `~>` (send) infix operator. Let's start from  `ProcessRef`. `ProcessRef` is a unique process identifier (UUID by default), it represents a process address in Parapet system and *must* be unique, it's recommended to use `ProcessRef` instead of a `Process` object directly unless you are sure you want otherwise. It's not prohibited to use `Process` object directly, however using a process reference may be useful in some scenarios. Let's consider one such case. Imagine we want to dynamically change current `Printer` process in our client so that it will store data in file on disk instead of printing it to the console. We can add a new event `ChangePrinter`:

```scala
case class ChangePrinter(printer: ProcessRef) extends Event
```

Then our client will look like this:

```scala
class PrinterClient[F[_]](private var printer: ProcessRef) extends Process[F] {

  import PrinterClient._
  import dsl._


  override def handle: Receive = {
    case Start => Print("hello world") ~> printer
    case ChangePrinter(newPrinter) => eval(printer = newPrinter)
  }
}

object PrinterClient {

  case class ChangePrinter(printer: ProcessRef) extends Event

}
```

This design cannot be achieved when using direct processes b/c it's not possible to send `Process`  objects,  processes are not serializable in general. One more thing, you can override a `Process#ref` field, only make sure it's unique otherwise Parapet system will return an error during the startup. 





You probably already noticed a new type `ProcessRef`. 

And finally we need to write our application that will run our processes:

```scala
import cats.effect.IO
import io.parapet.CatsApp
import io.parapet.core.Process

object PrinterApp extends CatsApp {
  override def processes: IO[Seq[Process[IO]]] = IO {
    val printer = new Printer[IO]
    val printerClient = new PrinterClient[IO](printer.ref)
    Seq(printer, printerClient)
  }
}
```

This is Cats Effect specific application, meaning it uses  Cats IO type under the hood



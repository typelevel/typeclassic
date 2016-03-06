package typeclassic

object Test {
  import Example.TestOps
  import Example.Semigroup.Ops
  val res1 = 333 <=> 444
  val res2 = 1 |+| 2
  val res3 = Example.Semigroup[Int]
  val res4 = Example.implicitly_[Ordering[Int]]

  @typeclass trait Semigroup[A] {
    def combine(x: A, y: A): A
  }
  object Semigroup {
    implicit val sgInt: Semigroup[Int] = new Semigroup[Int] {
      def combine(x: Int, y: Int) = x + y
    }
  }
  Semigroup[Int].combine(1, 2)
}

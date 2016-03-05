package typeclassic

object Test {
  import Example.TestOps
  import Example.Semigroup.Ops
  val res1 = 333 <=> 444
  val res2 = 1 |+| 2
  val res3 = Example.Semigroup[Int]
  val res4 = Example.implicitly_[Ordering[Int]]
}

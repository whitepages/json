package com.persist

import com.twitter.util.Try

import JsonOps._
import com.persist.Exceptions.MappingException
import scala.annotation.implicitNotFound
import scala.reflect.runtime.universe._

import shapeless._
import syntax.typeable._

package object json {

  trait ReadWriteCodec[T] extends ReadCodec[T] with WriteCodec[T]

  object ReadWriteCodec {
    trait SimpleCodec[T] extends ReadWriteCodec[T] {
      def write(x: T):Json = x
      def read(x: Json):T = x.asInstanceOf[T]
    }
  }

  @implicitNotFound(msg = "Cannot find ReadCodec for ${T}")
  trait ReadCodec[+T] {
    def read(json: Json): T
  }

  object ReadCodec extends LabelledProductTypeClassCompanion[ReadCodec] {
    abstract class SimpleCodec[T: TypeTag] extends ReadCodec[T] {
      def read(x: Json):T = Try(x.asInstanceOf[T]).getOrElse(throw new MappingException(s"Expected: ${typeOf[T]} but found $x"))
    }
    implicit object StringCodec extends SimpleCodec[String]
    implicit object IntCodec extends SimpleCodec[Int]
    implicit object BooleanCodec extends SimpleCodec[Boolean]
    implicit object LongCodec extends SimpleCodec[Long]
    implicit object ShortCodec extends SimpleCodec[Short]
    implicit object DoubleCodec extends SimpleCodec[Double]
    implicit object BigDecimalCodec extends SimpleCodec[BigDecimal]

    def extractSeq[T: ReadCodec](json: Json): Seq[T] =
      json.cast[JsonArray].map(
        _.map(implicitly[ReadCodec[T]].read(_))
      ) getOrElse (
        throw new MappingException(s"Expected JsonArray but found $json")
      )

    implicit def set[T: ReadCodec] = new ReadCodec[Set[T]] {
      def read(json: Json): Set[T] = extractSeq[T](json).toSet
    }

    implicit def list[T: ReadCodec] = new ReadCodec[List[T]] {
      def read(json: Json): List[T] = extractSeq[T](json).toList
    }

    def castOrThrow(json: Json): JsonObject = json.cast[JsonObject].getOrElse(throw new MappingException(s"Expected JsonObject but found $json"))

    implicit def simpleMap[V: ReadCodec] = new ReadCodec[Map[String, V]] {
      def read(json: Json): Map[String, V] = castOrThrow(json).mapValues(implicitly[ReadCodec[V]].read(_))
    }

    implicit def complexMap[K: ReadCodec, V: ReadCodec] = new ReadCodec[Map[K, V]] {
      def read(json: Json): Map[K, V] = castOrThrow(json).map { case (key, value) =>
        (implicitly[ReadCodec[K]].read(Json(key)), implicitly[ReadCodec[V]].read(value))
      }
    }

    implicit def option[T: ReadCodec] = new ReadCodec[Option[T]] {
      def read(json: Json): Option[T] = if (json == jnull) None else Some(implicitly[ReadCodec[T]].read(json))
    }

    implicit def readCodecInstance: LabelledProductTypeClass[ReadCodec] = new LabelledProductTypeClass[ReadCodec] {
      def emptyProduct = new ReadCodec[HNil] {
        // This will silently accept extra fields within a JsonObject
        // To change this behavior make sure json is a JsonObject and that it is empty
        def read(json: Json) = HNil
      }

      def product[F, T <: HList](name: String, FHead: ReadCodec[F], FTail: ReadCodec[T]) = new ReadCodec[F :: T] {
        def read(json: Json): F :: T = {
          val map = castOrThrow(json)
          val fieldValue = map.getOrElse(name, throw new MappingException(s"Expected field $name on JsonObject $map"))
          // Try reading the value of the field
          // If we get a mapping exception, intercept it and add the name of this field to the path
          // If we get another exception, don't touch!
          // Pitfall: if handle did not accept a PartialFunction, we could transform an unknow exception into a match exception
          val head: F = Try(FHead.read(fieldValue)).handle{ case MappingException(msg, path) => throw MappingException(msg, s"name/$path") }.get
          val tail = FTail.read(json)
          head :: tail
        }
      }

      def project[F, G](instance: => ReadCodec[G], to : F => G, from : G => F) = new ReadCodec[F] {
        def read(json: Json): F = from(instance.read(json))
      }
    }
  }

  @implicitNotFound(msg = "Cannot find WriteCodec for ${T}")
  trait WriteCodec[-T] {
    def write(obj: T): Json
  }

  object WriteCodec extends LabelledProductTypeClassCompanion[WriteCodec] {
    trait SimpleCodec[T] extends WriteCodec[T] {
      def write(x: T):Json = x
    }
    implicit object StringCodec extends SimpleCodec[String]
    implicit object IntCodec extends SimpleCodec[Int]
    implicit object BooleanCodec extends SimpleCodec[Boolean]
    implicit object LongCodec extends SimpleCodec[Long]
    implicit object ShortCodec extends SimpleCodec[Short]
    implicit object DoubleCodec extends SimpleCodec[Double]
    implicit object BigDecimalCodec extends SimpleCodec[BigDecimal]
    implicit def simpleMap[V: WriteCodec] = new WriteCodec[scala.collection.Map[String, V]] {
      def write(obj: scala.collection.Map[String, V]): JsonObject = obj.mapValues(toJson(_)).toMap
    }
    implicit def simpleImmutableMap[V: WriteCodec] = new WriteCodec[Map[String, V]] {
      def write(obj: Map[String, V]): JsonObject = obj.mapValues(toJson(_)).toMap
    }
    implicit val jsonObject = new WriteCodec[JsonObject] {
      def write(obj: JsonObject): JsonObject = obj
    }
    implicit def complexMap[K: WriteCodec, V: WriteCodec] = new WriteCodec[scala.collection.Map[K,V]] {
      def write(obj: scala.collection.Map[K, V]) = obj.map { case (key, value) =>
        (Compact(implicitly[WriteCodec[K]].write(key)), implicitly[WriteCodec[V]].write(value))
      }
    }
    implicit def iterable[V: WriteCodec] = new WriteCodec[Iterable[V]] {
      def write(obj: Iterable[V]): JsonArray = obj.map(toJson(_)).toSeq
    }
    implicit def option[V: WriteCodec] = new WriteCodec[Option[V]] {
      def write(obj: Option[V]): Json = obj.map(toJson(_)).getOrElse(jnull)
    }

    implicit def writeCodecInstance: LabelledProductTypeClass[WriteCodec] = new LabelledProductTypeClass[WriteCodec] {
      def emptyProduct = new WriteCodec[HNil] {
        def write(t: HNil): JsonObject = Map()
      }

      def product[F, T <: HList](name: String, FHead: WriteCodec[F], FTail: WriteCodec[T]) = new WriteCodec[F :: T] {
        def write(ft: F :: T) = {
          val head = FHead.write(ft.head)
          val tail = FTail.write(ft.tail).asInstanceOf[JsonObject]
          tail + (name -> head)
        }
      }

      def project[F, G](instance: => WriteCodec[G], to : F => G, from : G => F) = new WriteCodec[F] {
        def write(f: F) = instance.write(to(f))
      }
    }
  }

  def toJson[T](obj: T)(implicit ev: WriteCodec[T]): Json = ev.write(obj)
  def read[T](json: Json)(implicit ev: ReadCodec[T]): T = ev.read(json)
}
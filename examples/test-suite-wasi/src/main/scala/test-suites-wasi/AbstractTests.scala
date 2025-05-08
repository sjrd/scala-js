package testSuiteWASI

import org.scalajs.testsuite.javalib.util.{ListTest, CollectionTest}
import org.scalajs.testsuite.javalib.lang.IterableTest

object AbstractTests {
  def runListTests(test: ListTest) = {
    import test._
    addStringGetIndex()
    addIntGetIndex()
    addDoubleGetIndex()
    addCustomObjectsGetIndex()
    removeStringRemoveIndex()
    removeDoubleOnCornerCases()
    clearList()
    containsStringList()
    containedDoubleOnCornerCases()
    setString()
    test.iterator()
    toArrayObjectForList()
    toArraySpecificForList()
    listIterator()
    listIteratorPreviousThrowsNoSuchElementException()
    addIndex()
    indexOf()
    lastIndexOf()
    indexOfLastIndexOfDoubleCornerCases()
    subListBackedByList()
    iteratorSetRemoveIfAllowed()
    replaceAll()
    sortWithNaturalOrdering()
    sortWithComparator()
  }

  def runCollectionTest(test: CollectionTest) = {
    import test._
    testWithString()
    testWithInt()
    testWithDouble()
    testWithCustomClass()
    removeString()
    removeDoubleCornerCases()
    clear()
    containsString()
    containsDoubleCornerCases()
    iteratorString()
    toArrayObject()
    toArraySpecific()
    removeIf()
    // TODO: float to string
    // toStringCollectionDoubleEmpty()
    // toStringCollectionDoubleOneElement()
    // toStringCollectionDoubleHasCommaSpace()
    toStringCollectionAnyWithNull()
    toStringCollectionCustomClass()
  }

  def runIterableTest(test: IterableTest) = {
    import test._
    empty()
    simpleSum()
    iteratorThrowsNoSuchElementException()
  }
}

package apps.app212

import munit.FunSuite
import scala.util.{Try, Success, Failure}

class WireTest extends FunSuite : 
  
  test("Wire.eventFormat: serialize and deserialize Event.WordSubmitted") {
    val event = Event.WordSubmitted("exampleWord")
    assertEquals(Wire.eventFormat.decode(Wire.eventFormat.encode(event)), Success(event))
  }

  test("Wire.eventFormat: deserialize malformed inputs") {
    assert(Wire.eventFormat.decode(ujson.Obj("type" -> "InvalidEvent")).isFailure)
    assert(Wire.eventFormat.decode(ujson.Obj("type" -> "WordSubmitted")).isFailure) 
    assert(Wire.eventFormat.decode(ujson.Obj()).isFailure) 
  }

  test("PhaseViewFormat: serialize and deserialize PhaseView.Guessing") {
    val phase = PhaseView.Guessing
    assertEquals(PhaseViewFormat.decode(PhaseViewFormat.encode(phase)), Success(phase))
  }

  test("PhaseViewFormat: serialize and deserialize PhaseView.Validation") {
    val phase = PhaseView.Validation
    assertEquals(PhaseViewFormat.decode(PhaseViewFormat.encode(phase)), Success(phase))
  }

  test("PhaseViewFormat: serialize and deserialize PhaseView.Waiting") {
    val phase = PhaseView.Waiting
    assertEquals(PhaseViewFormat.decode(PhaseViewFormat.encode(phase)), Success(phase))
  }

  test("PhaseViewFormat: deserialize malformed inputs") {
    assert(PhaseViewFormat.decode(ujson.Obj("type" -> "UnknownPhase")).isFailure)
    assert(PhaseViewFormat.decode(ujson.Obj()).isFailure) 
  }

  test("StateViewFormat: serialize and deserialize StateView.Playing") {
    val state = StateView.Playing(
      phase = PhaseView.Guessing,
      currentPlayer = "player1",
      attempts = Map("player1" -> Vector("word1", "word2")),
      validation = Map("player1" -> Vector("valid1", "valid2")),
      scores = Map("player1" -> 10, "player2" -> 5)
    )
    assertEquals(StateViewFormat.decode(StateViewFormat.encode(state)), Success(state))
  }

  test("StateViewFormat: serialize and deserialize StateView.Finished") {
    val state = StateView.Finished(
      winnerIds = Set("player1", "player2"),
      scores = Map("player1" -> 10, "player2" -> 5),
      revealWord = Some("finalWord")
    )
    assertEquals(StateViewFormat.decode(StateViewFormat.encode(state)), Success(state))
  }

  test("StateViewFormat: deserialize malformed inputs") {
    assert(StateViewFormat.decode(ujson.Obj("type" -> "UnknownState")).isFailure)
    assert(StateViewFormat.decode(ujson.Obj("type" -> "Playing")).isFailure) 
    assert(StateViewFormat.decode(ujson.Obj("type" -> "Finished")).isFailure) 
    assert(StateViewFormat.decode(ujson.Obj()).isFailure) 
  }


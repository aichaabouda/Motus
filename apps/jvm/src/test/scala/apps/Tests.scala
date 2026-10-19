package apps
import app212.*
import cs214.webapp.server.{StateMachine}
import apps.app212.View
import cs214.webapp.utils.WebappSuite
import cs214.webapp.Action
class Tests extends WebappSuite[Event, GuessWordState, View] {

  val sm = Logic()
  private val logic: Logic = sm.asInstanceOf[Logic]

  test("init should create an initial state with unique secret word") {
    val userIds = Seq("player1", "player2")
    val state1 = sm.init(userIds)
    val state2 = sm.init(userIds)

    assert(state1.secretWord != state2.secretWord) 
  }

  test("init should fail with no players") {
    intercept[IllegalArgumentException] {
      sm.init(Seq.empty)
    }
  }

  test("validateGuess should handle words with duplicate letters correctly") {
    val secretWord = "apple"
    val guessedWord = "happy"
    val result = logic.validateGuess(secretWord, guessedWord)

    assert(result == Vector("i", "m", "c", "m", "i"))
  }

  test("validateGuess should return 'n' for words not in dictionary") {
    val secretWord = "apple"
    val guessedWord = "abcde"
    val result = logic.validateGuess(secretWord, guessedWord)

    assert(result == Vector("n", "n", "n", "n", "n"))
  }

  test("validateGuess should handle all incorrect letters") {
    val secretWord = "apple"
    val guessedWord = "birth"
    val result = logic.validateGuess(secretWord, guessedWord)

    assert(result == Vector("i", "i", "i", "i", "i"))
  }

  test("transition should fail for invalid word length") {
    val initialState = sm.init(Seq("player1", "player2"))
    val event = Event.WordSubmitted("life")

    intercept[AssertionError] {
      sm.transition(initialState)("player1", event)
    }
  }

  test("transition should handle end of game when attempts are exhausted") {
    val initialState = sm.init(Seq("player1", "player2")).copy(
      attempts = Map(
        "player1" -> Vector.fill(5)("guess"),
        "player2" -> Vector.fill(6)("adore")
      )
    )
    val event = Event.WordSubmitted("guess")

    val result = sm.transition(initialState)("player1", event)
    assert(result.isSuccess)

    val actions = result.get
    println(s"Actions: $actions")

    assert(actions.last.isInstanceOf[Action.Render[GuessWordState]])

    val finalState =
      actions.last match
        case Action.Render(state: GuessWordState) => state
        case _ => throw new IllegalStateException("Expected Render action with GuessWordState")

    println(s"Final state phase: ${finalState.phase}")
    assert(finalState.phase == Phase.Done(Some(initialState.secretWord)))
  }

  test("score should calculate 0 for all incorrect letters") {
    val result = logic.score(Vector("i", "i", "i", "i", "i"))
    assert(result == 0)
  }

  test("score should calculate correct total for mixed results") {
    val result = logic.score(Vector("c", "m", "i", "c", "m"))
    assert(result == 6) // 2 + 1 + 0 + 2 + 1
  }

  test("score should calculate maximum score for all correct letters") {
    val result = logic.score(Vector("c", "c", "c", "c", "c"))
    assert(result == 10) 
  }

  test("project should show correct phase view for each phase") {
    val stateGuessing = sm.init(Seq("player1", "player2"))
    val stateValidation = stateGuessing.copy(phase = Phase.ShowingValidation)
    val stateDone = stateGuessing.copy(phase = Phase.Done(Some(stateGuessing.secretWord)))

    assert(sm.project(stateGuessing)("player1").phaseView == PhaseView.Guessing)
    assert(sm.project(stateValidation)("player1").phaseView == PhaseView.Validation)
    assert(sm.project(stateDone)("player1").phaseView == PhaseView.Waiting)
  }

  test("project should show the current player's view") {
    val state = sm.init(Seq("player1", "player2"))
    val view = sm.project(state)("player1")

    assert(view.stateView.isInstanceOf[StateView.Playing])
    val playingView = view.stateView.asInstanceOf[StateView.Playing]
    assert(playingView.currentPlayer == "player1")
  }

  test("project should show the winner(s) at the end of the game") {
    val state = sm.init(Seq("player1", "player2")).copy(
      phase = Phase.Done(Some(sm.init(Seq("player1", "player2")).secretWord)),
      scores = Map("player1" -> 10, "player2" -> 8)
    )
    val view = sm.project(state)("player1")

    assert(view.stateView.isInstanceOf[StateView.Finished])
    val finishedView = view.stateView.asInstanceOf[StateView.Finished]
    assert(finishedView.winnerIds == Set("player1"))
  }

  test("remainingAttempts should return correct number for each player") {
    val state = sm.init(Seq("player1", "player2")).copy(
      attempts = Map(
        "player1" -> Vector("guess1", "guess2"),
        "player2" -> Vector("guess1")
      )
    )

    assert(state.remainingAttempts == Map("player1" -> 4, "player2" -> 5))
  }

  test("isGameOver should return true when secret word is guessed") {
    val state = sm.init(Seq("player1", "player2")).copy(
      attempts = Map(
        "player1" -> Vector("guess1", "secret"),
        "player2" -> Vector("guess1")
      ),
      secretWord = "secret"
    )

    assert(state.isGameOver)
  }

  test("isGameOver should return false when attempts are remaining") {
    val state = sm.init(Seq("player1", "player2")).copy(
      attempts = Map(
        "player1" -> Vector("guess1", "guess2"),
        "player2" -> Vector("guess1")
      )
    )

    assert(!state.isGameOver)
  }

  
}

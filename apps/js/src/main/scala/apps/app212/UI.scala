package apps
package app212

import cs214.webapp.*
import cs214.webapp.client.*
import cs214.webapp.client.graphics.WebClientAppInstance
import org.scalajs.dom
import scalatags.JsDom.all.*

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

import PhaseView.*
import StateView.*

/**
 * UI object for the Wordle Game application, which is a web-based game that allows users to guess a word.
 * It implements a client app for the web with functionalities to interact with the game and render the UI.
 * 
 * The main components include:
 * - Rendering the game state.
 * - Handling user input for word guesses.
 * - Displaying the game's progress (score, phases, attempts).
 * - Managing the different game states (playing, finished).
 */
@JSExportTopLevel("app212_ui")
object UI extends WSClientApp:
  def appId: String = "app212"
  def uiId: String = "html"

  /**
   * Initializes the UI instance, which manages the game state and user interactions.
   *
   * @param userId The unique identifier of the user.
   * @param sendMessage A function to send messages to the server.
   * @param target The DOM element where the UI will be rendered.
   * @return A new instance of the UIInstance class to manage the user interface.
   */
  def init(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element): ClientAppInstance =
    UIInstance(userId, sendMessage, target)

/**
 * This class represents the specific instance of the UI for a user, handling game states, 
 * input rendering, and event submission. It inherits from WebClientAppInstance 
 * to manage game state transitions and rendering.
 * 
 * @param userId The unique identifier of the user.
 * @param sendMessage A function to send messages to the server.
 * @param target The DOM element where the UI will be rendered.
 */
class UIInstance(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element)
    extends WebClientAppInstance[Event, View](userId, sendMessage, target):

  override val wire = app212.Wire

  /**
   * Renders the UI for the game based on the current state.
   *
   * @param userId The unique identifier of the user.
   * @param view The current view state of the game.
   * @return A fragment of HTML to be rendered on the page.
   */
  override def render(userId: UserId, view: View): Frag =
    frag(
      h2(b("GuessWord Game"), "...Try to find the correct word!"),
      renderView(view)
    )

  /**
   * Renders the game view based on the current state (Playing or Finished).
   *
   * @param view The current view state of the game.
   * @return A fragment of HTML to be displayed to the user.
   */
  def renderView(view: View): Frag = view.stateView match
    case Playing(phase, currentPlayer, attempts, validation, scores) =>
      frag(
        renderPhaseIndicator(phase),
        p(i(s"It's $currentPlayer's turn. Make a guess!")),
        renderInputField(currentPlayer == userId),
        renderAttempts(attempts, validation,phase==Validation),
        renderScores(scores)
      )
    case Finished(winnerIds, scores, revealWord) =>
      frag(
        if winnerIds.size == 1 then
          p(cls := "finished", s"Game Over ! The winner is : ${winnerIds.head}")
        else
          p(cls := "finished", s"Game Over ! The winners are : ${winnerIds.mkString(", ")}"),
        frag(
          revealWord match
            case Some(word) => p(cls := "revealed", s"The secret word was $word")
            case None => // Do nothing
        ), 
        renderScores(scores)
      )

  /** 
   * Renders an input field for the user to submit a word guess.
   * 
   * This method will only render the input field if it's the user's turn. It also ensures that 
   * the input is restricted to uppercase letters and that non-letter characters are removed.
   * 
   * @param isCurrentUser A flag indicating whether it is the current user's turn to input a word.
   * @return A fragment containing the input field and submission button.
   */
  private def renderInputField(isCurrentUser: Boolean): Frag =
    if isCurrentUser then
      div(
        cls := "input-field",
        input(
          id := "guess-input",
          placeholder := "Enter your word",
          maxlength := 5,
          autofocus := true,
          cls := "guess-input",
          oninput := { (e: dom.Event) =>
            val input = e.target.asInstanceOf[dom.html.Input]
            input.value = input.value.toUpperCase.replaceAll("[^A-Z]", "") // Only allows letters
          }
        ,
        onkeydown := { (e: dom.KeyboardEvent) =>
          if e.key == "Enter" then
            val input = e.target.asInstanceOf[dom.html.Input]
            val word = input.value.trim.toLowerCase
            if word.nonEmpty && word.length == 5 then
              submitWord(word)
              input.value = "" // Clears the input field after submission
        }
        ),
        button(
          cls := "submit-button",
          "Submit",
          onclick := { () =>
            val input = dom.document.getElementById("guess-input").asInstanceOf[dom.html.Input]
            val word = input.value.trim.toLowerCase
            if word.nonEmpty && word.length == 5 then
              submitWord(word)
              input.value = "" // Clears the input field after submission
          }
        )
      )
    else
      p(cls := "waiting", "Wait for your turn.")

  /**
   * Handles the submission of the user's guessed word.
   *
   * This function sends the guessed word to the server, allowing the game to validate the guess.
   *
   * @param word The word that the user has guessed.
   */
  private def submitWord(word: String): Unit =
    sendEvent(Event.WordSubmitted(word))

  /**
   * Renders the attempts made by all players and the feedback for each attempt.
   * 
   * Each attempt is accompanied by feedback showing whether each letter is correct, misplaced, or incorrect.
   * 
   * @param attempts A map of player IDs to their attempted words.
   * @param validation A map of player IDs to the validation feedback for each word attempt.
   * @return A fragment containing all the attempts and feedback for display.
   */
  private def renderAttempts(
      attempts: Map[UserId, Vector[String]],
      validation: Map[UserId, Vector[String]],
      isValidationPhase:Boolean
  ): Frag =
    div(
      cls := "attempts",
      attempts.toSeq.map { case (player, words) =>
        div(
          h4(s"Player: $player"),
          div(
            cls := "word-list",
            words.zip(validation.getOrElse(player, Vector.empty)).map { case (word, feedback) =>
              div(
                cls := "word",
                word.zip(feedback).zipWithIndex.map { case ((char, marker), index) =>
                  div(
                     id := s"letter-$player-$index",
                     cls := s"letter ${
                    if isValidationPhase then "animate" else ""
                  } ${getCssClassForMarker(marker.toString())}",
                    style := s"animation-delay: ${index * 0.3}s;", // Delay for each letter
                    char.toString
                  )
                }
              )
            }
          )
        )
      }
    )

  /**
   * Maps the feedback marker to a corresponding CSS class for visual indication.
   * 
   * The feedback markers indicate whether a letter is notInDictionnary('n'),correct ('c'), misplaced ('m'), or incorrect ('i').
   *
   * @param marker The marker indicating the letter's status ('c', 'm', or 'i').
   * @return The corresponding CSS class for styling the letter based on the feedback.
   */
  private def getCssClassForMarker(marker: String): String = marker match
    case "c" => "correct"
    case "m" => "misplaced"
    case "i" => "incorrect"
    case "n" => "notInDictionnary"
    case _   => ""

  /**
   * Renders the scores of all players.
   * 
   * This method sorts the scores in descending order and displays the scores with player names.
   * 
   * @param scores A map of player IDs to their scores.
   * @return A fragment displaying the scores for all players.
   */
  private def renderScores(scores: Map[UserId, Int]): Frag =
    div(
      cls := "scores",
      h3("Scores:"),
      ul(
        scores.toSeq.sortBy(-_._2).map { case (player, score) =>
          li(s"$player: $score points")
        }
      )
    )

  /**
   * Renders the current phase of the game (Guessing, Validation, or Waiting).
   * 
   * The phase indicator informs the user what part of the game they are currently in, guiding them to take
   * the appropriate action (e.g., guessing a word, waiting for feedback).
   *
   * @param phase The current phase of the game.
   * @return A fragment displaying the current phase.
   */
  private def renderPhaseIndicator(phase: PhaseView): Frag = phase match
    case Guessing     => p(cls := "phase-indicator", "Enter a word!")
    case Validation   => p(cls := "phase-indicator", "Feedback in progress...")
    case Waiting      => p(cls := "phase-indicator", "Wait for your turn.")

  /**
   * Custom CSS styles for the Wordle game UI.
   * 
   * This includes styles for the letter animations, buttons, input fields, and overall layout.
   *
   * @return A string of CSS styles to be applied to the app's HTML elements.
   */
  override def css: String = super.css +
    """
    | .app212 .word {
    |     display: flex;
    |     gap: 0.5rem;
    |     justify-content: center;
    |     margin-bottom: 1rem;
    | }
    | .app212 .letter {
    |    display: inline-flex;
    |    width: 3rem;
    |    height: 3rem;
    |    justify-content: center;
    |    align-items: center;
    |    font-size: 2rem;
    |    font-weight: bold;
    |    text-transform: uppercase;
    |    border: 2px solid #d3d6da;
    |    border-radius: 0.25rem;
    |    transition: transform 0.6s ease, background-color 0.3s ease, color 0.3s ease;
    |    backface-visibility: hidden;
    |    transform: rotateY(0);
    |    animation-fill-mode: forwards;
    |}
    |.app212 .letter.animate {
    |    animation: flip 0.5s ease-in-out;
    |}
    |@keyframes flip {
    |    0% {
    |        transform: rotateY(0);
    |    }
    |    50% {
    |        transform: rotateY(90deg);
    |    }
    |}
    |.app212 .letter.correct {
    |    background-color: #119c3d; /* Vert */
    |    color: white;
    |}
    |.app212 .letter.misplaced {
    |    background-color: #fab71b; /* Jaune */
    |    color: white;
    |}
    |.app212 .letter.incorrect {
    |    background-color: #b30715; /* Rouge */
    |    color: white;
    |}
    |.app212 .letter.notInDictionnary {
    |    background-color: #787c7e; /* Gris */
    |    color: white;
    |}
    | .scores {
    |     margin-top: 2rem;
    |     font-size: 1.2rem;
    | }
    | .finished {
    |     font-weight: bold;
    |     color: #333;
    | }
    |.revealed {
    |  color: #b50202;
    |  text-transform: uppercase;
    |  font-size: 1.5rem; /* Adjust size for larger text */
    |  font-style: italic; /* Make text italic */
    |  font-weight: bold;
    |  text-align: center;
    |}
    | .input-field {
    |     margin: 1rem 0;
    |     display: flex;
    |     gap: 1rem;
    |     justify-content: center;
    |     align-items: center;
    | }
    | .guess-input {
    |     padding: 0.5rem;
    |     font-size: 1rem;
    |     width: 10rem;
    |     text-transform: uppercase;
    |     border: 1px solid #d3d6da;
    |     border-radius: 0.25rem;
    | }
    | .submit-button {
    |     padding: 0.5rem 1rem;
    |     font-size: 1rem;
    |     background-color: #2830c9;
    |     color: white;
    |     border: none;
    |     border-radius: 0.25rem;
    |     cursor: pointer;
    |     text-transform: uppercase;
    | }
    | .submit-button:hover {
    |     background-color: #4c84e6;
    | }
    | .waiting {
    |     margin-top: 1rem;
    |     font-style: italic;
    |     color: #555;
    |     text-align: center;
    | }
    | .phase-indicator {
    |     font-size: 1.2rem;
    |     font-weight: bold;
    |     text-align: center;
    |     margin-bottom: 1rem;
    |     color: #2830c9;
    | }
    """.stripMargin

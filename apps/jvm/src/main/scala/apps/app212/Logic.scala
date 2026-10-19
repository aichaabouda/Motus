package apps
package app212

import cs214.webapp.*
import cs214.webapp.server.{StateMachine}
import ujson.Value

import scala.annotation.unused
import scala.util.{Random, Try}
import View.* 
import Event.*
import PhaseView.* 
import GuessWordState.*
import Dictionnary.* 
import apps.app212.Dictionnary.words

/**
 * The `Logic` object contains the core functionality and logic for the word-guessing game.
 * It includes the game rules, phase transitions, word validation, scoring, and state management.
 */
object Logic :
    // Dictionary of valid words for the game
    val dictionnary : List[String] = Dictionnary.allWords

/**
 * The `Logic` class implements the `StateMachine` for the GuessWord game.
 * It handles state transitions, event processing, score calculation, and the validation of guesses.
 */
class Logic extends StateMachine[Event, GuessWordState, View]:
    import Logic._

    // Application information (metadata about the app)
    val appInfo : AppInfo = AppInfo(
        id = "app212",
        name = "GuessWord",
        description = " Word-guessing game where you can compete against your friends to guess a five-letter word in six attempts.",
        year = 2024
    )
    
    // Wire for handling events
    override val wire = app212.Wire

    /**
     * Calculates the score based on the validation result of a guessed word.
     * The scoring is based on the correctness of each letter in the guess:
     * - 2 points for correct letters ("c")
     * - 1 point for misplaced letters ("m")
     * - 0 points for incorrect letters
     * 
     * @param result The validation result (Vector of markers such as "c", "m", "i").
     * @return The total score for the guessed word.
     */
    def score(result: Vector[String]): Int = {
        result.map {
            case "c" => 2 // "c" (correct) gives +2 points
            case "m" => 1 // "m" (misplaced) gives +1 point
            case _   => 0 // Anything else gives 0 points
        }.sum
    }

    /**
     * Validates a guessed word by comparing it with the secret word.
     * It marks each letter as either:
     * - "c" (correct) if the letter is in the correct position
     * - "m" (misplaced) if the letter is in the word but in the wrong position
     * - "i" (incorrect) if the letter is not in the word.
     * - "n" (notInDictionnary) if the letters form a word that is not in the dictionnary
     *
     * @param secretWord The secret word to be guessed.
     * @param guessedWord The word submitted by the player as a guess.
     * @return A Vector of validation markers for each letter in the guessed word.
     */
    def validateGuess(secretWord: String, guessedWord: String): Vector[String] = {
        require(secretWord.length == guessedWord.length, "Secret word and guessed word must have the same length.")
        

        if ( !dictionnary.contains(guessedWord.toLowerCase)) {
            return Vector.fill(secretWord.length)("n") // "n" for "not in dictionary"
        }


        // Initialize an array to hold the result of validation (default is "i" for incorrect)
        val result = Array.fill(secretWord.length)("i")

        

        // Step 1: Mark correct letters (green)
        var remainingSecret = secretWord
        var remainingGuess = guessedWord
        for (i <- secretWord.indices) {
            if (secretWord(i) == guessedWord(i)) {
                result(i) = "c" // Correct
                remainingSecret = remainingSecret.take(i) + "_" + remainingSecret.drop(i + 1)
                remainingGuess = remainingGuess.take(i) + "_" + remainingGuess.drop(i + 1)
            }
        }

        // Step 2: Mark misplaced letters (yellow)
        for (i <- remainingGuess.indices) {
            if (remainingGuess(i) != '_' && remainingSecret.contains(remainingGuess(i))) {
                result(i) = "m" // Misplaced
                remainingSecret = remainingSecret.replaceFirst(remainingGuess(i).toString, "_")
            }
        }

        // Return the result of validation
        result.toVector
    }

    /**
     * Initializes the state of the game.
     * This includes the list of players, the secret word, the current game phase, attempts, validation, and scores.
     *
     * @param clients A sequence of user IDs representing the players in the game.
     * @return The initial state of the game.
     */
    override def init(clients : Seq[UserId]): GuessWordState =
        require(clients.nonEmpty, "There must be at least one player.")
        
        GuessWordState(
            players = clients.toVector,                             // List of players
            secretWord = Random.shuffle(dictionnary).head,          // Randomly choose a secret word
            phase = Phase.Guessing,                                  // Initial phase is "Guessing"
            attempts = clients.map(id => id -> Vector.empty[String]).toMap,  // Initialize attempts as empty
            validation = clients.map(id => id -> Vector.empty[String]).toMap, // Initialize validation as empty
            scores = clients.map(id => id -> 0).toMap               // Initialize scores as 0
        )

    /**
     * Handles the transition from one game state to another based on events and the current phase.
     * 
     * @param state The current state of the game.
     * @param userId The user ID of the player making the move.
     * @param event The event that triggered the state transition (e.g., WordSubmitted).
     * @return A sequence of actions to be performed during the state transition.
     * @throws NotYourTurnException If the player is trying to make a move out of turn.
     * @throws IllegalMoveException If the move is invalid (e.g., wrong word length).
     */
    override def transition(state: GuessWordState)(userId: UserId, event: Event): Try[Seq[Action[GuessWordState]]] = Try {
        import Action.*
        state.phase match
            case Phase.Done(w) => throw IllegalMoveException("Game is already over!")
            case Phase.ShowingValidation =>
                throw AssertionError("Impossible!")

            case Phase.Guessing =>
                val GuessWordState(players, secretWord, phase, attempts, validation, scores) = state
                val WordSubmitted(word) = event.asInstanceOf[WordSubmitted]

                // Ensure it's the player's turn
                if state.players.head != userId then throw NotYourTurnException()

                // Validate the word length
                require(word.length == state.secretWord.length, s"Invalid word length! Expected ${state.secretWord.length} letters.")

                // Check if the guessed word is in the dictionary
                //val isWordInDictionary = dictionnary.contains(word.toLowerCase)
                val isCorrect = word == secretWord

                // Update attempts with the guessed word
                val newAttempts = state.attempts.updated(
                    userId,
                    state.attempts.getOrElse(userId, Vector()) :+ word 
                )

                // Validate the guessed word
                val valid = validateGuess(secretWord, word) 
                val newValidation = state.validation.updated(
                    userId,
                    state.validation.getOrElse(userId, Vector()) :+ valid.mkString 
                )

                // Update scores based on the guess result
                val newScores = scores.updated(
                    userId,
                    scores.getOrElse(userId, 0) + (if isCorrect then 12 else score(valid))
                )

                // Create the updated game state
                val updatedState = state.copy(
                    attempts = newAttempts,
                    validation = newValidation,
                    scores = newScores
                )

                val SHOW_VALIDATION_PAUSE_MS = 1500

                // Check if all players have reached 6 attempts
                val allPlayersReachedMaxAttempts = newAttempts.values.forall(_.length == 6)
                val revealWord = if updatedState.attempts.values.flatten.exists(_ == updatedState.secretWord) then None else Some(updatedState.secretWord)
                if allPlayersReachedMaxAttempts || isCorrect then
                    // If the game is over, determine the winner(s)
                    val winnerIds =
                        if isCorrect then Set(userId)
                        else {
                            val maxScore = newScores.values.max
                            newScores.filter(_._2 == maxScore).keys.toSet
                        }

                    Seq(
                        Render(updatedState.copy(phase = Phase.ShowingValidation)),
                        Pause(SHOW_VALIDATION_PAUSE_MS), // Pause to show the validation
                        Render(updatedState.copy(phase = Phase.Done(revealWord)))
                    )
                else
                    // If the game is not over, continue to the next phase
                    Seq(
                        Render(updatedState.copy(phase = Phase.ShowingValidation)),
                        Pause(SHOW_VALIDATION_PAUSE_MS),
                        Render(updatedState.copy(players = players.tail :+ players.head, phase = Phase.Guessing))
                    )
    }


    /**
     * Projects the current state into a view for the user.
     * It generates the view based on the game phase, the current player's turn, and the current attempts, validation, and scores.
     *
     * @param state The current state of the game.
     * @param userId The user ID of the player whose view is being projected.
     * @return A View object representing the current game state for the player.
     */
    override def project(state: GuessWordState)(userId: UserId): View = 
        val phaseView: PhaseView = state.phase match {
            case Phase.Guessing => PhaseView.Guessing
            case Phase.ShowingValidation => PhaseView.Validation
            case Phase.Done(w) => PhaseView.Waiting
        }
        // Determine the current player based on the game phase
        val currentPlayer = state.players.headOption.getOrElse(userId)
        // Create the state view based on the current phase and game state
        val stateView: StateView = state.phase match {
            case Phase.Guessing => // In "Guessing" phase, the player can submit a guess
                StateView.Playing(phaseView, currentPlayer, state.attempts, state.validation, state.scores)
            case Phase.ShowingValidation => // In "ShowingValidation" phase, the player sees the validation result of the guess
                StateView.Playing(phaseView, currentPlayer, state.attempts, state.validation, state.scores)
            case Phase.Done(revealWord) => // If the game is over, show the winners
                val winnerIds = state.scores.filter(_._2 == state.scores.values.max).keys.toSet
                StateView.Finished(winnerIds, state.scores, revealWord)
        }
        // Return the final view based on the state and phase
        View(stateView, phaseView)
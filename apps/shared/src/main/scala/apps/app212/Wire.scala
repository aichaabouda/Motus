package apps.app212
import cs214.webapp.*
import scala.util.{Failure, Success, Try}

/**
 * The `Wire` object handles the encoding and decoding of events and views 
 * into their JSON representations for the web application.
 */
object Wire extends AppWire[Event, View]:
  import Event.*
  import View.* 
  import ujson.*
  
  /**
   * Handles the JSON format for events, providing methods to encode 
   * an event into JSON and decode JSON back into an event.
   */
  override object eventFormat extends WireFormat[Event]:
    
    /**
     * Encodes an `Event` instance into a JSON representation.
     * @param event The event to encode.
     * @return The JSON representation of the event.
     */
    override def encode(event: Event): Value =
        event match
            case WordSubmitted(word) =>
                Obj("type" -> "WordSubmitted", "word" -> word)

    /**
     * Decodes a JSON object into an `Event` instance.
     * @param json The JSON representation of the event.
     * @return A `Try` containing the decoded event or a failure if decoding fails.
     */
    override def decode(json: Value): Try[Event] = Try:
        json("type").str match
            case "WordSubmitted" => WordSubmitted(json("word").str)
            case _ => throw DecodingException(f"Invalid event $json")

  /**
   * Handles the JSON format for views, providing methods to encode 
   * a view into JSON and decode JSON back into a view.
   */
  override object viewFormat extends WireFormat[View]:
    // A helper to encode and decode scores as a Map[String, Int].
    val scoresWire = MapWire(StringWire, IntWire)
    
    /**
     * Encodes a `View` instance into a JSON representation.
     * @param v The view to encode.
     * @return The JSON representation of the view.
     */
    override def encode(v: View): Value =
        Obj(
            "phase" -> PhaseViewFormat.encode(v.phaseView),  // Encodes the phase view.
            "state" -> StateViewFormat.encode(v.stateView)  // Encodes the state view.
        )
    
    /**
     * Decodes a JSON object into a `View` instance.
     * @param json The JSON representation of the view.
     * @return A `Try` containing the decoded view or a failure if decoding fails.
     */
    override def decode(json: Value): Try[View] = Try:
        View(
            StateViewFormat.decode(json("state")).get,  // Decodes the state view.
            PhaseViewFormat.decode(json("phase")).get  // Decodes the phase view.
        )

/**
 * Provides the JSON format for `PhaseView`, supporting encoding and decoding.
 */
object PhaseViewFormat extends WireFormat[PhaseView]:
  import PhaseView.*
  import ujson.*
  
  /**
   * Encodes a `PhaseView` instance into a JSON representation.
   * @param phaseView The phase view to encode.
   * @return The JSON representation of the phase view.
   */
  override def encode(phaseView: PhaseView): Value =
    phaseView match
        case Guessing => Obj("type" -> "Guessing")
        case Validation => Obj("type" -> "Validation")
        case Waiting => Obj("type" -> "Waiting")
        

  /**
   * Decodes a JSON object into a `PhaseView` instance.
   * @param json The JSON representation of the phase view.
   * @return A `Try` containing the decoded phase view or a failure if decoding fails.
   */
  override def decode(json: Value): Try[PhaseView] = Try:
    json("type").str match
        case "Guessing" => Guessing
        case "Validation" => Validation
        case "Waiting" => Waiting
        case _ => throw DecodingException(f"Unexpected phase view: $json")

/**
 * Provides the JSON format for `StateView`, supporting encoding and decoding.
 */
object StateViewFormat extends WireFormat[StateView]:
  import StateView.*
  import ujson.*
  import PhaseViewFormat.*
  import cs214.webapp.MapWire

  // Helpers for encoding/decoding Map and Vector structures.
  private val attemptsWire = MapWire(StringWire, VectorWire(StringWire))
  private val validationWire = MapWire(StringWire, VectorWire(StringWire))
  private val scoresWire = MapWire(StringWire, IntWire)

  /**
   * Encodes a `StateView` instance into a JSON representation.
   * @param stateView The state view to encode.
   * @return The JSON representation of the state view.
   */
  override def encode(stateView: StateView): Value =
    stateView match
      case Playing(phase, currentPlayer, attempts, validation, scores) =>
        Obj(
          "type" -> "Playing",
          "phase" -> apps.app212.PhaseViewFormat.encode(phase), // Encode phase view.
          "currentPlayer" -> currentPlayer,
          "attempts" -> attemptsWire.encode(attempts), // Encode attempts map.
          "validation" -> validationWire.encode(validation), // Encode validation map.
          "scores" -> scoresWire.encode(scores) // Encode scores map.
        )
      case Finished(winnerIds, scores, revealWord) =>
        Obj(
          "type" -> "Finished",
          "winnerIds" -> winnerIds.toSeq, // Convert Set to Seq for JSON encoding.
          "scores" -> scoresWire.encode(scores), // Encode scores map.
          "revealWord" -> revealWord.map(ujson.Str.apply).getOrElse(ujson.Null)
        )

  /**
   * Decodes a JSON object into a `StateView` instance.
   * @param json The JSON representation of the state view.
   * @return A `Try` containing the decoded state view or a failure if decoding fails.
   */
  override def decode(json: Value): Try[StateView] = Try {
    json("type") match {
      case ujson.Str("Playing") =>
        Playing(
          phase = apps.app212.PhaseViewFormat.decode(json("phase")).get, // Decode phase view.
          currentPlayer = json("currentPlayer").str, // Check if this exists and is a string
          attempts = attemptsWire.decode(json("attempts")).get, // Decode attempts map.
          validation = validationWire.decode(json("validation")).get, // Decode validation map.
          scores = scoresWire.decode(json("scores")).get // Decode scores map.
        )
      case ujson.Str("Finished") =>
        Finished(
          winnerIds = json("winnerIds").arr.map(_.str).toSet, // Convert JSON array to Set.
          scores = scoresWire.decode(json("scores")).get, // Decode scores map.
          revealWord = json.obj.get("revealWord").flatMap {
            case ujson.Str(word) => Some(word)
            case _ => None // Handle case where revealWord is missing or not a string
          }
        )
      case _ =>
        throw DecodingException(f"Unexpected state view: $json")
    }
  }

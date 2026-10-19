package apps.app212

import scala.io.Source

/**
 * `Dictionnary` object handles the management of a word dictionary loaded from a file.
 * It provides methods to load words, check if a word exists in the dictionary, 
 * and retrieve all the words.
 */
object Dictionnary:
  /**
   * Path to the file containing the dictionary words.
   * The file should be located in the `resources` directory of the project.
   */
  val filePath = "five_letters.txt"

  /**
   * List of words loaded from the dictionary file.
   * The words are cached in memory after loading to improve performance.
   */
  val words: List[String] = loadWords()

  /**
   * Loads words from the dictionary file.
   * 
   * @return A list of strings where each string represents a word from the dictionary file.
   * @throws java.io.IOException If the file cannot be found or read.
   */
  def loadWords(): List[String] = {
    val source = Source.fromResource(filePath) // Load the file from the resources directory
    try {
      source.getLines().toList
    } finally {
      source.close() // Ensure the file is closed after reading
    }
  }

  /**
   * Checks whether a given word exists in the dictionary.
   * The word is checked in a case-insensitive manner.
   * 
   * @param word The word to be checked.
   * @return `true` if the word exists in the dictionary, `false` otherwise.
   */
  def contains(word: String): Boolean = words.contains(word.toUpperCase)

  /**
   * Retrieves all words from the dictionary.
   * 
   * @return A list of all words in the dictionary.
   */
  def allWords: List[String] = words
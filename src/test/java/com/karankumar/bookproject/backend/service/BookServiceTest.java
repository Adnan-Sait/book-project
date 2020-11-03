/*
    The book project lets a user keep track of different books they would like to read, are currently
    reading, have read or did not finish.
    Copyright (C) 2020  Karan Kumar

    This program is free software: you can redistribute it and/or modify it under the terms of the
    GNU General Public License as published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY
    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
    PURPOSE.  See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with this program.
    If not, see <https://www.gnu.org/licenses/>.
 */

package com.karankumar.bookproject.backend.service;

import com.karankumar.bookproject.annotations.IntegrationTest;
import com.karankumar.bookproject.backend.entity.Tag;
import com.karankumar.bookproject.backend.entity.Author;
import com.karankumar.bookproject.backend.entity.Book;
import com.karankumar.bookproject.backend.entity.BookGenre;
import com.karankumar.bookproject.backend.entity.BookFormat;
import com.karankumar.bookproject.backend.entity.CustomShelf;
import com.karankumar.bookproject.backend.entity.PredefinedShelf;
import com.karankumar.bookproject.backend.entity.RatingScale;
import org.apache.commons.io.FileUtils;

import static com.karankumar.bookproject.backend.entity.PredefinedShelf.ShelfName.TO_READ;

import org.assertj.core.api.SoftAssertions;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@IntegrationTest
@DisplayName("BookService should")
class BookServiceTest {
    private final AuthorService authorService;
    private final BookService bookService;
    private final CustomShelfService customShelfService;
    private final TagService tagService;
    private final PredefinedShelfService predefinedShelfService;

    private PredefinedShelf toRead;
    private Author author;

    @Autowired
    BookServiceTest(AuthorService authorService, BookService bookService,
                    CustomShelfService customShelfService, TagService tagService,
                    PredefinedShelfService predefinedShelfService) {
        this.authorService = authorService;
        this.bookService = bookService;
        this.customShelfService = customShelfService;
        this.tagService = tagService;
        this.predefinedShelfService = predefinedShelfService;
    }

    @BeforeEach
    public void setup() {
        resetServices();
        toRead = predefinedShelfService.findByPredefinedShelfNameAndLoggedInUser(TO_READ);
        author = new Author("Test First Name", "Test Last Name");
    }

    private Book.BookBuilder validBook() {
        return Book.builder()
                   .title("Book Name")
                   .author(author)
                   .predefinedShelf(toRead);
    }

    private void resetServices() {
        bookService.deleteAll();
        authorService.deleteAll();
        customShelfService.deleteAll();
        tagService.deleteAll();
    }

    @Test
    @DisplayName("throw an exception when there is an attempt to save a null book")
    void throwExceptionWhenSavingANullBook() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> bookService.save(null));
    }

    @Test
    void notSaveBookWithoutAuthor() {
        SoftAssertions softly = new SoftAssertions();

        // when
        bookService.save(new Book("Book without author", null, toRead));

        // then
        softly.assertThat(authorService.count()).isZero();
        softly.assertThat(bookService.count()).isZero();
        softly.assertAll();
    }

    @Test
    void notSaveBookWithMaxNumberOfPagesExceeded() {
        SoftAssertions softly = new SoftAssertions();

        // given
        Book book = new Book("Book without author", new Author("First", "Last"), toRead);
        book.setNumberOfPages(Book.MAX_PAGES + 1);

        // when and then
        softly.assertThatThrownBy(() -> bookService.save(book))
              .isInstanceOf(TransactionSystemException.class);
        softly.assertThat(bookService.count()).isZero();
        softly.assertAll();
    }

    @Test
    void notSaveBookOutsidePageLimit() {
        SoftAssertions softly = new SoftAssertions();

        // given
        Book book = new Book("Book without author", new Author("First", "Last"), toRead);
        book.setPagesRead(Book.MAX_PAGES + 1);

        // when and then
        softly.assertThatThrownBy(() -> bookService.save(book))
              .isInstanceOf(TransactionSystemException.class);
        softly.assertThat(bookService.count()).isZero();
        softly.assertAll();
    }

    @Test
    @DisplayName("throw an exception when there is an attempt to delete a null book")
    void throwExceptionWhenDeletingANullBook() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> bookService.delete(null));
    }

    @Test
    void saveBookWithinPageLimit() {
        // given
        Book book = new Book("Book without author", new Author("First", "Last"), toRead);
        book.setPagesRead(Book.MAX_PAGES);
        book.setNumberOfPages(Book.MAX_PAGES);

        // when
        bookService.save(book);

        // then
        assertThat(bookService.count()).isOne();
    }

    @Test
    void notSaveBookWithoutPredefinedShelf() {
        SoftAssertions softly = new SoftAssertions();

        // given
        Book bookWithoutShelf = new Book("Title", new Author("First", "Last"), null);

        // when
        bookService.save(bookWithoutShelf);

        // then
        softly.assertThat(authorService.count()).isZero();
        softly.assertThat(bookService.count()).isZero();
        softly.assertAll();
    }

    @Test
    void saveValidBook() {
        // given
        SoftAssertions softly = new SoftAssertions();
        assertThat(bookService.count()).isZero();
        Book validBook = validBook().build();

        // when
        bookService.save(validBook);

        // then
        List<Book> filteredByTitle = bookService.findAll(validBook.getTitle());

        softly.assertThat(bookService.count()).isOne();
        softly.assertThat(bookService.findAll(validBook.getTitle()).size()).isOne();
        softly.assertThat(filteredByTitle).first().isEqualTo(validBook);
        softly.assertAll();
    }

    @Test
    void returnAllBooksWhenFilterIsEmpty() {
        assertEquals(bookService.findAll(), bookService.findAll(""));
    }

    @Test
    void returnAllBooksWhenFilterIsNull() {
        assertEquals(bookService.findAll(), bookService.findAll(null));
    }

    @Test
    void shouldCreateJsonRepresentationForBooks() throws IOException, JSONException {
        // given
        Book anotherValidBook = createBookAndSetAllAttributes().build();
        bookService.save(validBook().build());
        bookService.save(anotherValidBook);

        String expectedJsonString = FileUtils.readFileToString(
                new File("src/test/resources/exportedBooksSample.json"), "UTF8");

        // when
        String actualJsonString = bookService.getJsonRepresentationForBooksAsString();

        // then
        JSONAssert
                .assertEquals(expectedJsonString, actualJsonString, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    @Transactional
    void findSavedBook() {
        Book bookToSave = new Book("Book Name To Save", author, toRead);
        bookService.save(bookToSave);
        assertEquals(bookService.findById(bookToSave.getId()), bookToSave);
    }

    private Book.BookBuilder createBookAndSetAllAttributes() {
        return validBook()
                .title("Another Book Name")
                .numberOfPages(420)
                .pagesRead(42)
                .bookGenre(BookGenre.ADVENTURE)
                .bookFormat(BookFormat.PAPERBACK)
                .seriesPosition(3)
                .edition("2nd edition")
                .bookRecommendedBy("Peter Parker")
                .isbn("9780151010264")
                .yearOfPublication(2014)
                .customShelf(createAndSaveCustomShelf())
                .tags(createAndSaveTags())
                .rating(RatingScale.EIGHT)
                .dateStartedReading(LocalDate.of(2020, 7, 5))
                .dateFinishedReading(LocalDate.of(2020, 9, 5))
                .bookReview("Very good.");
    }

    private CustomShelf createAndSaveCustomShelf() {
        CustomShelf customShelf = customShelfService.createCustomShelf("My Shelf");
        customShelfService.save(customShelf);
        return customShelf;
    }

    private Set<Tag> createAndSaveTags() {
        Tag tag1 = new Tag("book");
        Tag tag2 = new Tag("adventure");
        tagService.save(tag1);
        tagService.save(tag2);
        return new HashSet<>(Arrays.asList(tag1, tag2));
    }

    @AfterEach
    void deleteBooks() {
        bookService.deleteAll();
    }
}

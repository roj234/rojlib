
usage:

```java
	interface X {
	    X instance = DAO.forType(X.class).newInstance(/*java.sql.Connection*/);

		@Query("SELECT * from `comments` where `user` = :userId LIMIT 50")
		List<Comment> getCommentsById(int userId);

		@Query("update `comments` SET `content` = :comment.text where `id` = :comment.id")
		void setCommentContent(Comment comment);

		@Query("insert into `comments` (`content`) VALUES(:comment.text)")
		void addComment(Comment comment);

		@Query(value = "delete from `comments` where `id` = :comment.id")
		int deleteComment(Comment comment);

		@Query(value = "delete from `comments` where `id` = :comment.id")
		int deleteComments(List<Comment> comment);
	}
```
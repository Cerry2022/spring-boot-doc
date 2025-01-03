package com.itstyle.doc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

// 使用lombok
@Data
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "md_books")
public class Books {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "book_id", nullable = false)
	private Integer bookId;

	@Column(nullable = false)
	private String bookName;

	@Column(nullable = false)
	private String identify;

	@Column(nullable = false)
	private Integer orderIndex;

	@Column(nullable = true)
	private String description;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private Integer privatelyOwned;

	@Column(nullable = true)
	private String private_token;

	@Column(nullable = false)
	private Integer status;

	@Column(nullable = false)
	private String editor;

	@Column(nullable = false)
	private Integer docCount;

	@Column(nullable = false)
	private String commentStatus;

	@Column(nullable = true)
	private Integer commentCount;

	@Column(nullable = false)
	private String cover;

	@Column(nullable = false)
	private String theme;

	@Column(nullable = false)
	private Timestamp createTime;

	@Column(nullable = true)
	private Integer memberId;

	@Column(nullable = false)
	private Timestamp modifyTime;

	@Column(nullable = true)
	private Integer version;

	@Column(nullable = false)
	private String roleName;

	@Column(nullable = false)
	private String createName;

}
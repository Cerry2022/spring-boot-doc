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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "md_options")
public class Options {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "option_id", nullable = false)
	private Integer optionId;

	@Column(nullable = false)
	private String optionTitle;

	@Column(nullable = false)
	private String optionName;

	@Column(nullable = false)
	private String optionValue;

	@Column(nullable = false)
	private String remark;
}
package com.icemelon404;

import lombok.*;

import javax.persistence.*;

@Getter
@NoArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "MEMBER")
@Access(AccessType.FIELD)
public class Member {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID")
    private long id;

    @NonNull @Column(name = "FIRST_NAME", nullable = false)
    private String firstName;

    @NonNull @Column(name = "SECOND_NAME", nullable = false)
    private String secondName;

    @Setter
    @NonNull @Column(name = "AGE")
    private int age;

    @ManyToOne
    @JoinColumn(name = "TEAM_ID")
    @Setter
    private Team team;


}

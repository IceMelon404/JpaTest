package com.icemelon404;

import lombok.*;

import javax.persistence.*;
import java.util.LinkedList;
import java.util.List;

@Getter
@Entity
@NoArgsConstructor
@RequiredArgsConstructor
public class Team {

    @Id @GeneratedValue
    @Column(name = "ID")
    private long id;

    @NonNull @Column(name = "team_name")
    private String teamName;

    @OneToMany(mappedBy = "team", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Member> members = new LinkedList<>();
}

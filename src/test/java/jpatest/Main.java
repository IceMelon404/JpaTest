package jpatest;

import com.icemelon404.Member;
import com.icemelon404.Team;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import javax.persistence.*;
import java.util.List;

public class Main {

    interface EntityManagerRunnable {
        void run(EntityManager em);
    }

    private static final EntityManagerFactory factory = Persistence.createEntityManagerFactory("jpatest");

    @AfterAll
    public static void closeFactory() {
        factory.close();
    }

    @BeforeEach
    public void init() {
        System.out.println("Initiating database");
        doInTransaction(manager-> {
            //엔티티 삭제를 위해서는 먼저 조회해야한다.
            Member member = manager.find(Member.class, getTestMember().getId());
            //엔티티 삭제, 쓰기 지연이 적용되고 트랜잭션이 커밋될때 플러시가 일어난다.
            manager.remove(member);
            Team team = manager.find(Team.class, getTestTeam().getId());
            manager.remove(team);
        });
    }

    private void doInTransaction(EntityManagerRunnable runnable) {
        //persistence.xml 의 jpatest 설정 정보를 이용해 EntityManagerFactory 생성. 코스트가 높다
        /*
            엔티티 매니저 생성. 엔티티 매니저는 영속성 컨텍스트에 엔티티를 저장한다.
            엔티티 매니저는 Connection 과 밀접한 관련이 있으므로 스레드간 공유되어서는 안된다.
        */
        EntityManager manager = factory.createEntityManager();
        //트랜잭션 획득
        EntityTransaction transaction = manager.getTransaction();
        try {
            //트랜잭션의 경계설정
            transaction.begin();
            runnable.run(manager);
            transaction.commit();
        } catch (Exception e) {
            e.printStackTrace();
            //예외가 던져지면 롤백
            transaction.rollback();
        } finally {
            //엔티티 매니저를 종료. 영속성 컨텍스트도 함께 종료되어짐.
            manager.close();
        }
    }

    @Test
    public void changeAndComparePersistedObject() {
        doInTransaction((em)-> {
            Member member = getTestMember();
            //영속성 컨텍스트에 엔티티 저장. 1차 캐시에 엔티티가 저장된다.
            em.persist(member);
            //영속성 컨텍스트의 변경 감지 기능
            member.setAge(23);
            //영속성 컨텍스트의 1차 캐시에서 엔티티를 찾으면 그대로 반환
            Member foundMember = em.find(Member.class, member.getId());
            Assertions.assertEquals(member, foundMember);
            Assertions.assertEquals(member.getAge(), foundMember.getAge());
        });
    }

    @Test
    public void persistAndFindMemberByJPQL() {
        doInTransaction((em)-> {
;           Member member = getTestMember();
            em.persist(member);

            TypedQuery<Member> query = em.createQuery("select m from Member m", Member.class);
            List<Member> members = query.getResultList();
            Assertions.assertFalse(members.isEmpty());
            Assertions.assertEquals(member, members.get(0));
        });
    }

    @Test
    public void findEntityFromDB() {
        Member member = getTestMember();
        doInTransaction((em)->{em.persist(member);});
        doInTransaction((em)-> {
            long id = member.getId();
            //1차 캐시에 엔티티가 존재하지 않을 경우 데이터베이스에서 엔티티를 가져옴
            Member foundMember = em.find(Member.class, id);
            Assertions.assertNotEquals(member, foundMember);
        });
    }

    @Test
    public void saveMemberWithTeam() {
        doInTransaction(em -> {
            Member member = getTestMember();
            Team team = getTestTeam();
            em.persist(team);
            member.setTeam(team);
            em.persist(member);
        });
    }

    @Test
    public void saveMemberWithTeamNotPersisted() {
        Assertions.assertThrows(Exception.class, () -> doInTransaction((em) -> {
            Member member = getTestMember();
            Team team = getTestTeam();
            member.setTeam(team);
            //연관된 Team 엔티티 역시 영속 상태여야한다
            em.persist(member);
        }));
    }

    @Test
    public void saveMembersAndGetByTeam() {
        persistMembersWithSameTeam();
        doInTransaction(em-> {
            Team team = em.find(Team.class, getTestTeam().getId());
            Assertions.assertNotNull(team.getMembers());
            Assertions.assertFalse(team.getMembers().isEmpty());
        });
    }

    @Test
    public void persistMembersWithTeamAndRemoveTeam() {
        Team team = persistMembersWithSameTeam();
        doInTransaction(em -> {
            Team foundTeam = em.find(Team.class, team.getId());
            //cascade = CascadeType.REMOVE 가 적용되어 있어 외래키로 팀을 참조하는 멤버 엔티티들도 지워진다.
            em.remove(foundTeam);
        });
    }

    @Test
    public void removeAndFindMember() {
        Member member = getTestMember();
        doInTransaction(em-> {
            em.persist(member);
        });
        doInTransaction(em -> {
            Member foundMember = em.find(Member.class, member.getId());
            em.remove(foundMember);
            Member member1 = em.find(Member.class, member.getId());
            Assertions.assertNull(member1);
        });
    }

    @Test
    public void persistMembersWithTeamAndRemoveFromTeam() {
        Team team = getTestTeam();
        doInTransaction(em -> {
            em.persist(team);
            Member member = getTestMember();
            member.setTeam(team);
            em.persist(member);
        });
        doInTransaction(em-> {
            Team foundTeam = em.find(Team.class, team.getId());
            Assertions.assertFalse(foundTeam.getMembers().isEmpty());
            foundTeam.getMembers().clear();
        });

        doInTransaction(em -> {
            Team foundTeam = em.find(Team.class, team.getId());
            Assertions.assertTrue(foundTeam.getMembers().isEmpty());
            //orphanRemoval 은 다대다 관계에서는 사용해서는 안된다.
        });
    }

    @Test
    public void orphanRemovalTest() {
        doInTransaction(em -> {
            Team team = getTestTeam();
            Member member = getTestMember();
            em.persist(team); //스냅샷 등록
            team.getMembers().add(member);
            member.setTeam(team);
            em.persist(member); //member 가 영속성 컨텍스트에 저장됌. team 역시 영속성 컨텍스트에 저장되어 있으므로 연관관계가 설정됌.
            team.getMembers().clear();
            //커밋시 플러시 호출. team 의 경우 persist 를 이용해 영속성 컨텍스트에 저장될때 스냅샷이 등록되는데, 이와 비교해서 변경 감지되는 것이 없음.
            //따라서 orphanRemoval 이 호출되지 않음.
        });
    }

    @Test
    public void orphanRemovalTest2() {
        doInTransaction(em -> {
            Team team = getTestTeam();
            Member member = getTestMember();
            team.getMembers().add(member);
            em.persist(team); //team 이 영속성 컨텍스트에 저장됌. cascade 에 의해 member 역시 저장되지만, member 는 team 을 참조하지 않음. cascade.persist 는 단순히 영속시키는 역할만 수행
            team.getMembers().clear();
            Assertions.assertNull(member.getTeam());
            //커밋시 플러시 호출. team 의 경우 persist 를 이용해 영속성 컨텍스트에 저장될때 스냅샷이 등록되는데,
            //이와 비교해서 member 객체가 삭제됌. 따라서 member 가 team 을 참조하고 있지 않음에도 orphanRemoval이 호출됌.
        });
    }

    @Test
    public void orphanRemovalTest3() {
        doInTransaction(em -> {
            Team team = getTestTeam();
            Member member = getTestMember();
            em.persist(team); //스냅샷 등록
            team.getMembers().add(member);
            member.setTeam(team);
            em.persist(member); //member 가 영속성 컨텍스트에 저장됌. team 역시 영속성 컨텍스트에 저장되어 있으므로 연관관계가 설정됌.

            em.flush(); //플러시를 직접 호출. SQL 저장소에 있던 team 과 member insert 쿼리가 실행됌. team 에 member 가 들어있는 상태로 스냅샷이 찍힘.
            team.getMembers().clear();
            //커밋시 플러시 호출. team 의 member 가 스냅샷과 달리 사라졌으므로 변경 감지 -> orphanRemoval 호출
        });
    }


    private Team persistMembersWithSameTeam() {
        Team team = getTestTeam();
        doInTransaction(em -> {
            em.persist(team);
            for (int i = 0; i < 100; i++) {
                Member member = new Member("user" + i, "", 22);
                member.setTeam(team);
                em.persist(member);
            }
        });
        return team;
    }

    private Member getTestMember() {
        String username = "user";
        String secondName = "secondName";
        return new Member(username, secondName, 22);
    }

    private Team getTestTeam() {
        return new Team("teamName");
    }
}

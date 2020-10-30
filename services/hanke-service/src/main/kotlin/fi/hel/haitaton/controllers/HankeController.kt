package fi.hel.haitaton.controllers

import fi.hel.haitaton.data.Hanke
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import java.util.*

@RestController
@RequestMapping("/hankkeet")
open class HankeController {

    /**
     * Get one hanke with hankeId.
     */
    @GetMapping
    fun getHankeById(@RequestParam(name="hankeId") hankeId:String): ResponseEntity<Any>  {
        if(hankeId == null)
            return ResponseEntity.badRequest().body("id puuttuu")
       else
           return ResponseEntity.ok(getHanke())
    }


    fun getHanke(): Hanke {
        return Hanke("AAA123", "Mannerheimintien remontti remonttinen", Date(), Date(), "Risto",1 )
    }

}